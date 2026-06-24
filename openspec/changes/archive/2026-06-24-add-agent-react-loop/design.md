# Design: 新增 Agent 与 ReAct 循环

## 架构概览

### 新增模块在 DDD 分层中的位置

在现有六边形架构中，新增组件全部位于领域层（`domain/agent/`），不新增基础设施层代码：

```
Interfaces (CLI)
  AgentCli ← 变更：展示 ReAct 执行轨迹
    │
Application (编排层)
  AgentAppService ← 变更：重构为委托 Agent 端口
  AgentResponseDTO ← 变更：新增 steps、totalTurns 字段
    │
Domain ────────────────────────────────────────────
  domain.agent (★ 本次新增)
    Agent (接口/端口)
    DefaultAgent (默认实现)
    AgentRequest (值对象)
    AgentResponse (值对象)
    ReActLoop (领域服务)
    ReActStep (值对象)
    Conversation (领域聚合)
    ContextBuildService (领域服务)
  domain.chat (复用 ChatModelPort)
  domain.tool (复用 ToolRegistry、Tool、ToolResult)
  domain.task (复用 AgentTask)
  domain.session (复用 AgentSession)
    │
Infrastructure ────────────────────────────────────
  (无新增 — 全部复用现有适配器)
    DeepSeekChatModel、ToolRegistryImpl、5 个工具实现
```

### 依赖方向

```
interfaces.cli → application.service → domain.agent (Agent 端口)
domain.agent.DefaultAgent → domain.agent.ReActLoop
domain.agent.ReActLoop → domain.chat.ChatModelPort (已有端口)
domain.agent.ReActLoop → domain.tool.ToolRegistry (已有端口)
domain.agent.ContextBuildService → domain.chat.ChatRequest
domain.agent.Conversation → domain.message.Message
infrastructure 无变更 (全部复用)
```

**关键原则：** 所有新增代码位于 `domain/agent/`，不跨越领域层边界，不引入到基础设施层的依赖。

### 与现有模块的集成点

| 现有模块 | 集成方式 | 变更类型 |
|----------|---------|---------|
| `ChatModelPort` | 构造器注入到 `ReActLoop`，每轮调用 `chat(ChatRequest)` | 无变更 |
| `ChatRequest` | `ContextBuildService.build()` 使用 Builder 模式构造 | 无变更 |
| `ChatResponse` / `ChatChoice` | `ReActLoop` 解析 `finishReason` 和 `message.toolCalls` | 无变更 |
| `ToolRegistry` | 构造器注入到 `ReActLoop`，调用 `execute(ToolCall, ToolExecutionContext)` | 无变更 |
| `ToolResult.toToolMessage()` | `Conversation.addToolMessage()` 内部使用 | 无变更 |
| `ToolDefinition` | 通过 `ToolRegistry.definitions()` 获取工具列表 | 无变更 |
| `AgentTask` | `ReActLoop` 驱动其状态机（start → increaseTurn → complete/fail/hitMaxTurns） | 无变更 |
| `AgentSession` / `SessionAppService` | `DefaultAgent` 通过 sessionId 关联会话 | 无变更 |
| `AgentAppService` | 重构为注入 `Agent` 端口，委托执行 | **变更** |
| `AgentResponseDTO` | 新增 `from(AgentResponse)` 工厂方法 | **变更** |
| `AgentCli` | 新增 ReAct 执行轨迹展示 | **变更** |
| `Main.java` | 新增 Agent 端口 + 实现的组装和注入 | **变更** |

---

## 领域模型

### 1. Agent 端口接口

```java
package com.flyagent.domain.agent;

/**
 * Agent 领域端口。
 *
 * <p>定义 Agent 执行的统一入口。领域层定义契约，
 * 不同 Agent 策略（ReAct、Plan-and-Execute）通过不同实现提供。</p>
 */
public interface Agent {
    /**
     * 执行 Agent 任务。
     *
     * @param request Agent 请求（包含用户输入、workspace、maxTurns 等）
     * @return Agent 响应（包含最终回答和执行轨迹）
     */
    AgentResponse run(AgentRequest request);
}
```

### 2. AgentRequest 值对象

```java
public class AgentRequest {
    private final String sessionId;       // 会话 ID（可选）
    private final String userInput;       // 用户输入任务（必填）
    private final Path workspace;         // 工作目录（必填，必须存在）
    private final int maxTurns;           // 最大 ReAct 轮次（默认 8）

    // Builder 构造，build() 时校验
}
```

| 字段 | 类型 | 必填 | 默认值 | 校验规则 |
|------|------|------|--------|---------|
| `sessionId` | String | 否 | null | — |
| `userInput` | String | 是 | — | 不能为 null 或空白 |
| `workspace` | Path | 是 | — | 不能为 null；`Files.isDirectory() == true` |
| `maxTurns` | int | 否 | 8 | 范围 [1, 100]，超出自动 clamp 并 warn |

### 3. AgentResponse 值对象

```java
public class AgentResponse {
    private final boolean success;            // 是否成功完成
    private final String finalAnswer;         // 最终回答（success=true 时）
    private final String error;               // 失败原因（success=false 时）
    private final List<ReActStep> steps;      // ReAct 执行轨迹
    private final int totalTurns;             // 总执行轮次

    // 工厂方法：AgentResponse.success(finalAnswer, steps, totalTurns)
    // 工厂方法：AgentResponse.error(error, steps, totalTurns)
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `success` | boolean | true=任务成功完成，false=异常终止 |
| `finalAnswer` | String | success=true 时有值 |
| `error` | String | success=false 时有值，人类可读的失败原因 |
| `steps` | List\<ReActStep\> | 完整执行步骤列表，按时间顺序 |
| `totalTurns` | int | 已完成的总轮次数 |

### 4. ReActStep 值对象

```java
public class ReActStep {
    private final int turn;                    // 轮次序号（从 1 开始）
    private final String thought;              // 思考摘要（简短）
    private final String actionName;           // 工具名称（有工具调用时）
    private final Map<String, Object> actionArguments;  // 工具参数
    private final String observation;          // 工具执行结果（截断后）
    private final String finalAnswer;          // 最终回答（无工具调用时）
    private final boolean success;             // 本步骤是否成功
    private final String error;                // 本步骤错误信息

    // Builder 构造
}
```

**ReActStep 的三种形态：**

| 形态 | thought | actionName | observation | finalAnswer | 触发条件 |
|------|---------|-----------|-------------|-------------|---------|
| Tool Call | 有 | 有 | 有 | null | LLM 返回 tool_calls |
| Final Answer | 有 | null | null | 有 | LLM 返回 content 且无 tool_calls |
| Error | 有 | null | null | null | LLM 失败 / 工具执行失败 |

### 5. Conversation 领域聚合

```java
public class Conversation {
    /**
     * 构造 Conversation。
     * @param maxMessages 最大保留消息数（含 System Message），默认 20
     * @param maxToolResultChars 单条工具结果最大字符数，默认 4000
     */
    public Conversation(int maxMessages, int maxToolResultChars);

    public void addUserMessage(String content);
    public void addAssistantMessage(String content);
    public void addAssistantToolCalls(List<ToolCall> toolCalls);
    public void addToolMessage(String toolCallId, String content);
    public List<Message> getMessages();   // System Message 始终在首位
    public void clear();                  // 清空所有非 System 消息
    public int size();
}
```

**内部数据结构：**
- `SystemMessage systemMessage` — 不可变，始终保留在首位
- `Deque<Message> historyMessages` — 非 System 消息队列，FIFO

**截断策略：**
1. **工具结果截断：** `addToolMessage()` 时，若 content 超过 `maxToolResultChars`（默认 4000），调用 `TextTruncator.truncate()` 截断并附加 `[... truncated ...]` 标记
2. **消息窗口截断：** `getMessages()` 返回时，System Message + 最近 `(maxMessages - 1)` 条历史消息。超出窗口的最旧消息自动丢弃

**线程安全：** 当前不保证线程安全。在同步 CLI 场景下，单线程访问足够。

### 6. ContextBuildService 领域服务

```java
public class ContextBuildService {
    /**
     * 构建 ChatRequest。
     *
     * @param conversation   当前会话上下文
     * @param toolDefinitions 可用工具定义列表
     * @param workspacePath  当前工作目录路径
     * @return 完整 ChatRequest（含 System Prompt、历史消息、工具定义）
     */
    public ChatRequest build(
            Conversation conversation,
            List<ToolDefinition> toolDefinitions,
            Path workspacePath);
}
```

**构建内容：**
1. 生成 System Prompt（包含 Agent 身份、ReAct 指令、workspace 路径、当前日期）
2. 获取历史消息列表（System Message 始终在第 0 位）
3. 注入工具定义列表
4. 构造 `ChatRequest`（thinking=true, stream=false, reasoningEffort="high"）

**System Prompt 模板：**

```
You are FlyAgent, a CLI coding agent running on the user's local machine.

You operate in a ReAct (Reasoning + Acting) loop:
- **Thought**: Briefly explain your next step.
- **Action**: Call a tool when you need to read files, list directories,
  run commands, or modify the project.
- **Observation**: The tool result will be provided. Never fabricate
  observations — only use actual tool outputs.
- **Final Answer**: Provide your final response to the user when the task
  is complete. Do NOT ask the user for permission or confirmation.

Available tools are listed in the function definitions. Use them to:
- Read and analyze files
- List directory structures
- Run shell commands (with approval)
- Write or modify files (with approval)
- Create project structures

Current workspace: {workspacePath}
Current date: {currentDate}

Important rules:
1. Always use tools to gather information — do not guess.
2. If a tool fails, report the error and try an alternative approach.
3. Do not call tools that are not in the available tools list.
4. Stop when the user's task is complete and provide a concise Final Answer.
```

### 7. ReActLoop 领域服务

```java
public class ReActLoop {
    private final ChatModelPort chatModel;
    private final ToolRegistry toolRegistry;

    public ReActLoop(ChatModelPort chatModel, ToolRegistry toolRegistry);

    /**
     * 执行 ReAct 循环。
     *
     * @param agentRequest   用户请求
     * @param toolContext    工具执行上下文
     * @param contextBuilder 上下文构建服务
     * @param conversation   会话上下文
     * @param task           任务聚合根（用于状态机管理）
     * @return AgentResponse
     */
    public AgentResponse execute(
            AgentRequest agentRequest,
            ToolExecutionContext toolContext,
            ContextBuildService contextBuilder,
            Conversation conversation,
            AgentTask task);
}
```

### 8. DefaultAgent 默认实现

```java
public class DefaultAgent implements Agent {
    private final ChatModelPort chatModel;
    private final ToolRegistry toolRegistry;
    private final SessionAppService sessionService;
    private final ApprovalHandler approvalHandler;

    // 构造器注入

    @Override
    public AgentResponse run(AgentRequest request) {
        // 1. 校验输入（userInput 非空、workspace 存在）
        // 2. 获取或创建会话
        // 3. 创建 AgentTask，注入 Conversation
        // 4. 添加 UserMessage 到 Conversation
        // 5. 创建 ContextBuildService 和 ToolExecutionContext
        // 6. 创建 ReActLoop 并委托执行
        // 7. 返回 AgentResponse
    }
}
```

---

## 核心流程设计

### ReAct 循环完整流程

```
DefaultAgent.run(request)
  │
  ├── 1. 校验 AgentRequest
  ├── 2. 创建/获取 AgentSession
  ├── 3. 创建 AgentTask（CREATED）
  ├── 4. 创建 Conversation
  ├── 5. 添加 UserMessage → Conversation
  │
  └── ReActLoop.execute(request, toolContext, contextBuilder, conversation, task)
        │
        ├── task.start() → RUNNING
        │
        └── while (task.canContinue()):  // status==RUNNING && currentTurn < maxTurns
              │
              ├── 构建 ChatRequest
              │     contextBuilder.build(conversation, toolDefs, workspace)
              │     → ChatRequest { messages, tools, thinking=true }
              │
              ├── 调用 LLM
              │     chatModel.chat(chatRequest) → ChatResponse
              │     ├── 捕获 ApiException → task.fail() → 返回 AgentResponse.error()
              │
              ├── 解析 ChatResponse
              │     ChatChoice = response.firstChoice()
              │     ├── choice == null → task.fail() → 返回 AgentResponse.error()
              │     │
              │     AssistantMessage msg = choice.getMessage()
              │     │
              │     ├── 情况 A: Final Answer
              │     │     finishReason != "tool_calls" && !msg.hasToolCalls() && msg.hasContent()
              │     │     → 记录 ReActStep(finalAnswer)
              │     │     → conversation.addAssistantMessage(content)
              │     │     → task.complete(content) → COMPLETED
              │     │     → 返回 AgentResponse.success()
              │     │
              │     ├── 情况 B: Tool Calls
              │     │     msg.hasToolCalls()
              │     │     → thought = extractThought(msg)  // 取 content 前 200 字符
              │     │     → conversation.addAssistantToolCalls(toolCalls)
              │     │     │
              │     │     └── for each ToolCall:
              │     │           ├── toolRegistry.execute(toolCall, toolContext) → ToolResult
              │     │           ├── 记录 ReActStep(thought, actionName, actionArgs, observation)
              │     │           ├── conversation.addToolMessage(toolCallId, observation)
              │     │           └── (工具失败也正常记录，循环继续)
              │     │
              │     │     → task.increaseTurn()
              │     │     → continue 下一轮
              │     │
              │     └── 情况 C: 空响应
              │           !msg.hasContent() && !msg.hasToolCalls()
              │           → task.fail() → 返回 AgentResponse.error()
              │
              └── 循环结束：达到 maxTurns 但未完成
                    → task.hitMaxTurns() → HIT_MAX_TURNS
                    → 返回 AgentResponse.error("Reached maximum turns")
```

### LLM 响应解析逻辑

```java
ChatChoice choice = response.firstChoice();
if (choice == null) {
    // 空响应
    task.fail("Model returned empty response");
    return AgentResponse.error("No response from model", steps, currentTurn);
}

AssistantMessage msg = choice.getMessage();
String finishReason = choice.getFinishReason();

// 情况 1: Final Answer — content 非空 + 无 tool_calls
if (!msg.hasToolCalls() && msg.hasContent()
        && !"tool_calls".equals(finishReason)) {
    // 记录 Final Answer step，结束循环
}

// 情况 2: Tool Calls — 有 tool_calls
if (msg.hasToolCalls()) {
    // 逐个执行工具，写入 Observation，继续循环
}

// 情况 3: 既无 content 也无 tool_calls
// 返回失败
```

### 工具执行协调流程

```
对每个 ToolCall (按 DeepSeek 返回顺序依次执行)：
  1. 获取工具名称: toolCall.getFunction().getName()
  2. 查找工具: toolRegistry.find(name)
  3. 如果未找到:
     - 生成失败 Observation: "Unknown tool: {name}"
     - 记录 ReActStep(success=false, error="Unknown tool")
     - 写入 conversation.addToolMessage(id, "Error: Unknown tool: {name}")
     - 继续执行下一个 ToolCall
  4. 如果找到:
     - 调用 toolRegistry.execute(toolCall, toolContext)
     - ToolRegistry 内部处理：参数解析 → 审批检查 → 执行 → 异常捕获
     - ToolResult.isSuccess():
       - 成功: Observation = result.getContent() (经 Conversation 截断)
       - 失败: Observation = "Error: " + result.getError()
  5. 记录 ReActStep
  6. 写入 conversation.addToolMessage(toolCallId, observation)
```

### AgentTask 状态机驱动

ReActLoop 在以下时间点操作 AgentTask 状态：

```
CREATED ──task.start()──→ RUNNING ──task.complete(content)──→ COMPLETED
                              │
                              ├──task.fail(reason)──→ FAILED
                              │
                              └──task.hitMaxTurns()──→ HIT_MAX_TURNS

每轮结束后: task.increaseTurn() → currentTurn++
循环条件: task.canContinue() → status==RUNNING && currentTurn < maxTurns
```

---

## 异常处理策略

### 异常处理矩阵

| 场景 | 处理位置 | 处理方式 | 对 Agent 的影响 |
|------|---------|---------|----------------|
| userInput 为空 | `DefaultAgent` | 立即抛出 `IllegalArgumentException` | 调用方捕获，不启动 Agent |
| workspace 不存在 | `DefaultAgent` | 立即抛出 `IllegalArgumentException` | 调用方捕获，不启动 Agent |
| LLM API 请求失败 (`ApiException`) | `ReActLoop` | `task.fail()`，返回 `AgentResponse.error()` | 终止执行，保留已执行 steps |
| LLM 返回空 choices | `ReActLoop` | `task.fail()`，返回 `AgentResponse.error()` | 终止执行 |
| 工具未注册 | `ToolRegistryImpl.execute()` | 返回 `ToolResult.error("Unknown tool: ...")` | 生成失败 Observation，继续下一轮 |
| 工具参数解析失败 | `ToolRegistryImpl.execute()` | 返回 `ToolResult.error()` | 生成失败 Observation，继续下一轮 |
| 工具执行时抛出异常 | `ToolRegistryImpl.execute()` (兜底) | 捕获，返回 `ToolResult.error()` | 生成失败 Observation，继续下一轮 |
| 安全违规 (`SecurityException`) | `ToolRegistryImpl.execute()` | 返回 `ToolResult.error()` (带 security_violation 标记) | 生成失败 Observation，继续下一轮 |
| 达到 maxTurns | `ReActLoop` | `task.hitMaxTurns()`，返回 `AgentResponse.error()` | 终止执行，返回已完成 steps |
| 用户拒绝审批 | `ApprovalHandler` 返回 false | 返回 `ToolResult.error("Approval denied")` | 生成拒绝 Observation，模型可调整策略 |

### 分层错误处理原则

```
Domain Layer (ReActLoop)
  - 捕获 ApiException → 终止执行
  - 解析 ToolResult.isSuccess() → 决定是否继续
  - 不捕获 RuntimeException → 传播到 CLI 层
        │
Infrastructure Layer (ToolRegistryImpl)
  - 捕获 SecurityException → ToolResult.error
  - 捕获所有 Exception → ToolResult.error
  - 绝不抛出非受检异常给上层
```

---

## 数据流

### Conversation 消息顺序示例（3 轮对话后）

```
[0] SystemMessage:     "You are FlyAgent, a CLI coding agent..."
[1] UserMessage:       "帮我分析这个项目结构"
[2] AssistantMessage:  (toolCalls: [list_files, read_file])    ← 第 1 轮
[3] ToolMessage:       (toolCallId=call_1, "Found pom.xml...")
[4] ToolMessage:       (toolCallId=call_2, "This is a Java Maven project...")
[5] AssistantMessage:  (toolCalls: [read_file])                ← 第 2 轮
[6] ToolMessage:       (toolCallId=call_3, "<project>...</project>")
[7] AssistantMessage:  (content="This project is FlyAgent...") ← Final Answer
```

### 消息窗口滑动

当消息数超过 20 条时：
```
[0] SystemMessage:     (始终保留)
[1] UserMessage:       (第 N-18 条)
...
[19] AssistantMessage: (最新一条)
```
最旧的非 System 消息被丢弃，新消息追加到末尾。

---

## 集成变更

### AgentAppService 重构

**现状：** 直接调用 `chatModel.chat()`，单轮对话。

**目标：** 注入 `Agent` 端口，委托执行多轮 ReAct。

```java
public class AgentAppService {
    private final Agent agent;  // 新增
    private final SessionAppService sessionService;  // 已有

    public AgentAppService(Agent agent, SessionAppService sessionService) {
        this.agent = agent;
        this.sessionService = sessionService;
    }

    public AgentResponseDTO handleUserInput(String userInput) {
        // 校验会话
        if (!sessionService.hasActiveSession()) {
            return AgentResponseDTO.error("No active session.");
        }

        AgentSession session = sessionService.getCurrentSession();

        // 构建 AgentRequest
        AgentRequest request = AgentRequest.builder()
                .sessionId(session.getId().getValue())
                .userInput(userInput)
                .workspace(session.getWorkspace().getPath())
                .build();

        // 委托 Agent 执行
        AgentResponse response = agent.run(request);

        // 转换为 DTO
        return AgentResponseDTO.from(response);
    }
}
```

### AgentResponseDTO 扩展

```java
public class AgentResponseDTO {
    // 已有字段
    private final boolean success;
    private final String content;
    private final boolean hasToolCalls;
    private final TokenUsage tokenUsage;
    private final String errorMessage;

    // 新增字段
    private final List<ReActStepDTO> steps;  // ReAct 执行轨迹
    private final int totalTurns;             // 总轮次数

    // 新增工厂方法
    public static AgentResponseDTO from(AgentResponse agentResponse);
}
```

### CLI 展示层变更

AgentCli 在展示 Agent 回复时同时展示 ReAct 执行轨迹：

```
> 帮我分析这个项目结构

  Thought: Need to explore the project directory first.
  Action: list_files {"path":".", "maxDepth":3}
  Observation: Found pom.xml, src/ directory, prd/ directory...

  Thought: Read the Maven configuration for project details.
  Action: read_file {"path":"pom.xml", "maxChars":12000}
  Observation: This is a Java 17 project with DDD architecture...

  Final Answer:
  This project is a Java 17 CLI coding agent called FlyAgent.
```

### Main.java 依赖组装变更

```java
// 现有（不变）
ChatModelPort chatModel = new DeepSeekChatModel(dsConfig, objectMapper);
ToolRegistry toolRegistry = new ToolRegistryImpl();
toolRegistry.registerAll(...);

// 新增
ReActLoop reActLoop = new ReActLoop(chatModel, toolRegistry);
DefaultAgent agent = new DefaultAgent(chatModel, toolRegistry, sessionService, approvalHandler);

// 变更
AgentAppService agentService = new AgentAppService(agent, sessionService);
// 原: AgentAppService agentService = new AgentAppService(chatModel, sessionService);
```

---

## CodingChatModel 端口决策

**决定：不新建 `CodingChatModel` 端口，直接复用 `ChatModelPort`。**

PRD 中提到的 `CodingChatModel` / `ModelRequest` / `ModelResponse` / `ModelToolCall` 概念，在现有代码中已有等价实现：

| PRD 概念 | 现有实现 | 覆盖能力 |
|----------|---------|---------|
| `CodingChatModel` | `ChatModelPort` | `chat(ChatRequest) → ChatResponse` |
| `ModelRequest` | `ChatRequest` | messages + tools + thinking + stream |
| `ModelResponse` | `ChatResponse` | choices + usage |
| `ModelToolCall` | `ToolCall` | id + type + function(name, arguments) |

**不新建端口的原因：**
1. `ChatModelPort` 已完全覆盖所有功能需求
2. 新端口会导致接口签名高度重合，违反 DRY 原则
3. Agent 层对 LLM 没有超出 `ChatModelPort` 的抽象需求
4. 保持架构简洁，减少不必要的抽象层

---

## 文件变更汇总

### 新增文件（8 个）

```
src/main/java/com/flyagent/domain/agent/
  Agent.java                     # Agent 领域端口接口
  DefaultAgent.java              # Agent 默认实现
  AgentRequest.java              # Agent 请求值对象
  AgentResponse.java             # Agent 响应值对象
  ReActLoop.java                 # ReAct 循环领域服务
  ReActStep.java                 # ReAct 执行步骤值对象
  Conversation.java              # 会话上下文领域聚合
  ContextBuildService.java       # 上下文构建领域服务
```

### 变更文件（4 个）

```
src/main/java/com/flyagent/
  application/service/AgentAppService.java     # 重构：注入 Agent 端口
  application/dto/AgentResponseDTO.java        # 扩展：新增 steps、totalTurns
  interfaces/cli/AgentCli.java                 # 变更：展示 ReAct 执行轨迹
  Main.java                                    # 变更：组装 Agent 依赖链
```

### 新增测试文件（5 个）

```
src/test/java/com/flyagent/domain/agent/
  AgentRequestTest.java          # AgentRequest 单元测试
  ConversationTest.java          # Conversation 单元测试
  ContextBuildServiceTest.java   # ContextBuildService 单元测试
  ReActLoopTest.java             # ReActLoop Mock 组件测试
  DefaultAgentTest.java          # DefaultAgent Mock 组件测试
```
