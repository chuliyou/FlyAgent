# FlyAgent 第三次开发 PRD：Agent 与 ReAct 循环

版本：v0.1  
对应一期 PRD：`prd/一期PRD-Claude-Code风格CLI-Agent.md`  
对应第一次开发：项目搭建与 DeepSeek API 客户端  
对应第二次开发：基础工具能力  
技术栈：Java 17 + Maven  
开发定位：实现 FlyAgent Agent 编排层，通过 ReAct 循环协调 LLM 调用和工具执行。

## 1. 背景

第一次开发完成 DeepSeek API 客户端、消息格式、工具格式和完整 `chat` 方法。第二次开发完成基础工具层，包括读取文件、写入文件、列出目录、执行 shell 命令和创建项目结构。

第三次开发需要把模型能力和工具能力串联起来，实现真正的 Agent 执行核心：

```text
用户任务 -> 构建上下文 -> 调用 LLM -> 解析 Thought/Action -> 执行工具 -> 生成 Observation -> 再次调用 LLM -> Final Answer
```

本阶段完成后，FlyAgent 应具备最小可用 Agent 能力：用户提出任务后，Agent 能在有限轮次内自主选择工具、读取或操作本地环境，并基于工具结果输出最终回答。

## 2. 开发目标

第三次开发交付以下能力：

1. 实现 Agent 核心对象。
2. 实现 ReAct 有限循环。
3. 实现上下文构建。
4. 协调 DeepSeek LLM 调用。
5. 协调 ToolRegistry 工具执行。
6. 将工具执行结果转换为 Observation。
7. 支持 Final Answer 输出。
8. 支持最大轮次控制。
9. 支持工具失败后的错误 Observation。
10. 支持基础会话上下文。
11. 支持执行轨迹输出，包括 Thought 摘要、Action、Observation、Final Answer。

## 3. 非目标

第三次开发暂不实现：

1. 完整 CLI 交互体验。
2. MySQL 持久化。
3. Redis 运行态缓存。
4. 多 Agent 协作。
5. 长任务规划器。
6. Git diff 展示。
7. 自动 Git commit。
8. 复杂工具审批 UI。
9. 流式输出。
10. 插件化工具加载。

## 4. 用户价值

第三次开发完成后，用户可以让 Agent 完成简单代码任务，例如：

1. “帮我分析这个项目结构。”
2. “读取 pom.xml 并解释依赖。”
3. “创建一个 Maven Java 项目结构。”
4. “运行 mvn test 并解释结果。”
5. “创建一个 README.md，写入项目说明。”

这些任务需要 Agent 自行决定是否调用工具，而不是用户手动指定每一步工具调用。

## 5. 核心流程

### 5.1 ReAct 执行闭环

```text
User Input
  -> ContextBuildService 构建消息
  -> CodingChatModel.chat
  -> Assistant Thought + Action 或 Final Answer
  -> 如果是 Action，调用 ToolRegistry
  -> ToolResult 转为 Observation
  -> Observation 加入 Conversation
  -> 下一轮 LLM 调用
  -> 直到 Final Answer 或达到 maxTurns
```

### 5.2 示例流程：分析项目结构

用户输入：

```text
帮我分析这个项目结构
```

Agent 执行：

```text
Thought: 需要先查看项目目录。
Action: list_files {"path":".","maxDepth":3}
Observation: 找到 pom.xml、src、prd、技术方案。

Thought: 需要读取 Maven 配置和 README。
Action: read_file {"path":"pom.xml","maxChars":12000}
Observation: 已读取 pom.xml。

Final Answer: 该项目是一个 Java Maven 项目...
```

## 6. 功能需求

### 6.1 Agent 入口

需要实现 Agent 统一入口：

```java
public interface Agent {
    AgentResponse run(AgentRequest request);
}
```

`AgentRequest`：

```java
public class AgentRequest {
    private String sessionId;
    private String userInput;
    private Path workspace;
    private int maxTurns;
}
```

`AgentResponse`：

```java
public class AgentResponse {
    private boolean success;
    private String finalAnswer;
    private String error;
    private List<ReActStep> steps;
}
```

要求：

1. `userInput` 不能为空。
2. `workspace` 必须存在。
3. `maxTurns` 默认 8。
4. 任务完成后返回 Final Answer。
5. 任务失败时返回可读错误和已执行 steps。

### 6.2 ReActLoop

必须实现 ReAct 循环服务：

```java
public class ReActLoop {
    public AgentResponse run(AgentRequest request);
}
```

循环规则：

1. 每轮先构建 LLM 请求上下文。
2. 调用 `CodingChatModel.chat`。
3. 如果返回 Final Answer，结束任务。
4. 如果返回 tool calls，逐个转换为 Action。
5. 调用 `ToolRegistry.execute` 执行工具。
6. 将 ToolResult 转换为 Observation。
7. Observation 作为 `tool` 消息写回 Conversation。
8. 轮次加一。
9. 超过 maxTurns 时停止并返回未完成状态。

### 6.3 ReActStep

需要定义 ReAct 执行步骤：

```java
public class ReActStep {
    private int turn;
    private String thought;
    private String actionName;
    private Map<String, Object> actionArguments;
    private String observation;
    private String finalAnswer;
    private boolean success;
    private String error;
}
```

要求：

1. `thought` 是简短思考摘要，不展示完整推理链。
2. `actionName` 来自模型返回的工具调用。
3. `observation` 必须来自真实工具结果。
4. 工具执行失败时，`success=false`，`error` 写入失败原因。
5. 所有步骤按执行顺序保留。

### 6.4 Conversation

第三次开发需要实现基础会话上下文：

```java
public class Conversation {
    public void addUserMessage(String content);
    public void addAssistantMessage(String content);
    public void addAssistantToolCalls(List<ModelToolCall> toolCalls);
    public void addToolMessage(String toolCallId, String content);
    public List<ChatMessage> recentMessages(int maxMessages);
    public void clear();
}
```

要求：

1. 每次用户输入追加 user message。
2. LLM 普通回复追加 assistant message。
3. LLM tool calls 追加 assistant tool call message。
4. 工具结果追加 tool message。
5. 默认保留最近 20 条消息。
6. 工具结果过长时截断。

### 6.5 ContextBuildService

需要实现上下文构建服务：

```java
public class ContextBuildService {
    public ModelRequest build(AgentRequest request, Conversation conversation);
}
```

构建内容：

1. System Prompt。
2. 用户消息。
3. 最近会话消息。
4. 工具定义列表。
5. ReAct 输出约束。
6. workspace 信息。

System Prompt 必须包含：

```text
You are FlyAgent, a CLI coding agent.
Use ReAct style:
- Thought: provide a brief next-step summary.
- Action: call tools when local information or changes are needed.
- Observation: wait for tool results. Never fabricate observations.
- Final Answer: answer the user when the task is complete.
Use only available tools to access files or run commands.
```

### 6.6 LLM 响应解析

Agent 需要识别两类 LLM 响应：

1. Final Answer。
2. Tool Calls。

要求：

1. 优先使用 DeepSeek tool calls。
2. 当 `finishReason=tool_calls` 时必须进入工具执行。
3. 当没有 tool calls 且 content 非空时视为 Final Answer。
4. 如果模型返回空响应，返回失败并提示。
5. 如果工具参数 JSON 无法解析，返回错误 Observation。

### 6.7 工具执行协调

工具执行由 Agent 协调，不由模型直接执行。

要求：

1. Agent 根据 tool call name 查找工具。
2. 未找到工具时生成失败 Observation。
3. 工具执行异常时捕获并转成失败 Observation。
4. 工具结果必须回传给 Conversation。
5. 一轮中存在多个 tool calls 时，按返回顺序执行。
6. shell 和写文件审批逻辑沿用第二次开发工具层能力。

### 6.8 最大轮次控制

默认最大轮次：`8`

达到最大轮次后：

1. 停止继续调用 LLM。
2. 返回 `success=false`。
3. `error` 提示达到最大轮次。
4. 返回已执行的 ReActStep。
5. 如果已有部分有用结果，应在最终响应中摘要说明。

### 6.9 执行轨迹输出

AgentResponse 需要保留执行轨迹，供 CLI 后续展示：

```text
Thought: ...
Action: ...
Observation: ...
Final Answer: ...
```

要求：

1. Action 显示工具名和关键参数。
2. Observation 显示截断后的工具结果。
3. 不输出完整隐藏推理链。
4. 工具失败也要展示错误 Observation。

## 7. 数据结构

### 7.1 AgentRequest

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `sessionId` | string | 否 | 会话 ID |
| `userInput` | string | 是 | 用户输入任务 |
| `workspace` | Path | 是 | 工作目录 |
| `maxTurns` | int | 否 | 最大 ReAct 轮次 |

### 7.2 AgentResponse

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `success` | boolean | 是否成功完成 |
| `finalAnswer` | string | 最终回答 |
| `error` | string | 失败原因 |
| `steps` | List<ReActStep> | ReAct 执行轨迹 |

### 7.3 ReActStep

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `turn` | int | 轮次 |
| `thought` | string | 思考摘要 |
| `actionName` | string | 工具名 |
| `actionArguments` | map | 工具参数 |
| `observation` | string | 工具结果 |
| `finalAnswer` | string | 最终回答 |
| `success` | boolean | 本步骤是否成功 |
| `error` | string | 错误信息 |

## 8. 技术约束

1. Java 17。
2. Maven。
3. 不依赖 LangChain、AutoGPT、Semantic Kernel 等 Agent 框架。
4. Agent 层依赖 `CodingChatModel` 接口，不直接依赖 DeepSeek HTTP DTO。
5. Agent 层依赖 `ToolRegistry`，不直接依赖具体工具类。
6. Conversation 暂存内存，不接入 MySQL。
7. 默认非流式调用。
8. ReAct Loop 必须有限轮次。

## 9. 推荐工程结构

```text
src/main/java/com/flyagent/
  application/
    service/
      AgentAppService.java
  domain/
    agent/
      Agent.java
      DefaultAgent.java
      AgentRequest.java
      AgentResponse.java
      ReActLoop.java
      ReActStep.java
      ReActStepType.java
      Conversation.java
      ContextBuildService.java
      AgentExecutionException.java
    model/
      chat/
        ChatMessage.java
        ModelRequest.java
        ModelResponse.java
        ModelToolCall.java
    port/
      CodingChatModel.java
  infrastructure/
    tools/
      ToolRegistry.java
      ToolExecutionContext.java
```

## 10. 异常处理

| 场景 | 处理方式 |
| --- | --- |
| LLM 请求失败 | 返回失败 AgentResponse，保留已执行 steps |
| LLM 返回空 choices | 返回失败，提示模型响应为空 |
| 工具不存在 | 生成失败 Observation，允许模型下一轮修正 |
| 工具执行失败 | 生成失败 Observation，允许模型下一轮修正 |
| 工具参数非法 | 生成失败 Observation |
| 达到最大轮次 | 停止执行，返回已完成步骤 |
| 用户拒绝审批 | 生成拒绝 Observation，并结束或让模型继续判断 |

## 11. 验收标准

第三次开发完成后，应满足：

1. 可以通过 `Agent.run` 提交用户任务。
2. Agent 可以构建包含 system prompt、用户消息、历史消息和工具定义的 ModelRequest。
3. Agent 可以调用 `CodingChatModel.chat`。
4. Agent 可以识别 Final Answer 并结束任务。
5. Agent 可以识别 tool calls 并调用 ToolRegistry。
6. Agent 可以把 ToolResult 转成 Observation。
7. Agent 可以把 Observation 追加回 Conversation。
8. Agent 可以执行多轮 ReAct 循环。
9. Agent 达到 maxTurns 后会停止。
10. AgentResponse 包含完整执行轨迹。
11. 工具执行失败不会导致进程崩溃。
12. 未知工具调用会生成失败 Observation。
13. shell 和写文件审批逻辑可以通过工具层生效。
14. 单元测试覆盖 Final Answer、单工具调用、多工具调用、工具失败、未知工具、达到最大轮次等场景。

## 12. 测试用例

### 12.1 Final Answer

1. Mock LLM 直接返回 Final Answer。
2. Agent 不调用工具。
3. AgentResponse `success=true`。
4. steps 包含 Final Answer。

### 12.2 单工具调用

1. Mock LLM 第一轮返回 `read_file` tool call。
2. Mock ToolRegistry 返回文件内容。
3. Mock LLM 第二轮返回 Final Answer。
4. AgentResponse 包含 Action 和 Observation。

### 12.3 多工具调用

1. Mock LLM 返回 `list_files` 和 `read_file`。
2. Agent 按顺序执行工具。
3. Conversation 写入多个 tool message。
4. 下一轮 LLM 可以看到多个 Observation。

### 12.4 工具失败

1. Mock LLM 返回不存在文件的 `read_file`。
2. ToolRegistry 返回失败 ToolResult。
3. Agent 将失败结果作为 Observation。
4. 下一轮 LLM 可基于错误修正或输出失败说明。

### 12.5 未知工具

1. Mock LLM 返回未注册工具。
2. Agent 不崩溃。
3. 生成失败 Observation。
4. steps 记录错误。

### 12.6 最大轮次

1. Mock LLM 每轮都返回工具调用。
2. `maxTurns=3`。
3. Agent 执行 3 轮后停止。
4. AgentResponse `success=false`，error 提示达到最大轮次。

## 13. 开发里程碑

### M1：Agent 基础模型

交付：

1. `AgentRequest`。
2. `AgentResponse`。
3. `ReActStep`。
4. `Conversation`。

### M2：上下文构建

交付：

1. `ContextBuildService`。
2. ReAct System Prompt。
3. 工具定义注入。
4. 最近消息窗口。

### M3：ReActLoop

交付：

1. LLM 调用编排。
2. Final Answer 判断。
3. tool calls 判断。
4. 最大轮次控制。

### M4：工具执行协调

交付：

1. ToolRegistry 调用。
2. ToolResult 到 Observation 转换。
3. 多工具顺序执行。
4. 工具错误处理。

### M5：测试与验收

交付：

1. Mock LLM。
2. Mock ToolRegistry。
3. ReActLoop 单元测试。
4. Agent 端到端内存测试。

## 14. 风险与应对

| 风险 | 影响 | 应对 |
| --- | --- | --- |
| LLM 持续调用工具不结束 | 无限循环风险 | 强制 maxTurns |
| LLM 返回未知工具 | 无法执行 | 生成失败 Observation |
| 工具输出过长 | 上下文膨胀 | Observation 截断 |
| 工具失败后 Agent 中断 | 用户体验差 | 失败结果也作为 Observation |
| Thought 暴露完整推理链 | 不符合可控展示 | 只展示简短思考摘要 |
| Agent 层耦合 DeepSeek 实现 | 后续难扩展 | 只依赖 CodingChatModel 端口 |

## 15. 总结

第三次开发的目标是实现 FlyAgent 的 Agent 执行核心。它不再只是能调用模型或单独执行工具，而是能够通过 ReAct 循环把 LLM 决策和本地工具执行连接起来。

完成后，FlyAgent 将具备最小可用的 Agent 能力：理解用户任务、选择工具、执行工具、观察结果，并最终输出回答。这将为后续 CLI 交互完善、持久化、审批体验和更复杂编码任务打下核心基础。
