# FlyAgent 第三次开发技术方案：Agent 与 ReAct 循环

版本：v1.0  
对应 PRD：`prd/第三次开发PRD-Agent与ReAct循环.md`  
对应第一次开发：项目搭建与 DeepSeek API 客户端（M1+M3）  
对应第二次开发：基础工具能力（5 个工具 + 工具层基础设施）  
技术栈：Java 17 + Maven + Jackson + OkHttp + SLF4J/Logback  
开发定位：实现 Agent 编排层，通过 ReAct 循环协调 LLM 调用和工具执行

---

## 1. 技术方案概述

### 1.1 业务目标

第三次开发的目标是将已完成的 **LLM 调用能力**（DeepSeek ChatModelPort）与 **工具执行能力**（ToolRegistry + 5 个基础工具）串联起来，实现 FlyAgent 的 Agent 执行核心。完成后，用户可以提交自然语言任务，Agent 在有限轮次内自主选择工具、读取或操作本地环境，并基于工具结果输出最终回答。

### 1.2 核心设计决策

| 决策点 | 选择 | 理由 | 代价 |
|--------|------|------|------|
| LLM 端口 | **复用现有 `ChatModelPort`**，不新建 `CodingChatModel` | `ChatModelPort.chat(ChatRequest)` 已支持 tools、messages、thinking 等全部能力；新建端口会增加不必要的抽象层 | PRD 中的 `CodingChatModel` 概念不再作为独立端口存在 |
| Agent 入口 | **`Agent` 接口作为领域端口**，`DefaultAgent` 作为实现 | 遵循六边形架构，Agent 层只依赖端口接口，可替换执行策略（ReAct / Plan-and-Execute 等） | 多一层间接调用 |
| 会话上下文 | 新建 **`Conversation` 领域聚合**，内存存储，最近 20 条消息窗口 | 独立管理 Agent 对话历史，不侵入现有 `AgentSession`（会话生命周期）和 `AgentTask`（任务状态机） | 存在三个相关领域对象，需明确职责边界 |
| 上下文构建 | 新建 **`ContextBuildService` 领域服务** | 将 System Prompt、工具定义、历史消息组装为 ChatRequest 的逻辑集中管理 | 引入额外的组装层 |
| 执行轨迹 | **`ReActStep` 不可变值对象** | 与现有领域对象风格一致（不可变 + Builder），线程安全，便于序列化 | 每步需新建对象 |
| 无第三方框架 | **零外部 Agent 框架依赖** | 保持项目轻量，DeepSeek 原生 function calling 足够 | 需自行实现 ReAct 循环逻辑 |

### 1.3 架构图描述

```
Interfaces (CLI)
    |
    v
Application (编排层)
    AgentAppService ──→ Agent (domain port)
                             |
                             v
                        DefaultAgent ──→ ReActLoop (domain service)
                                             |
                    ┌────────────────────────┼────────────────────────┐
                    |                        |                        |
                    v                        v                        v
              ChatModelPort           ToolRegistry          ContextBuildService
              (domain port)          (domain port)          (domain service)
                    |                        |                        |
                    v                        v                        v
Infrastructure        DeepSeekChatModel    ToolRegistryImpl    Conversation (domain)
(适配器)              (infrastructure)     (infrastructure)    (domain aggregate)
```

**分层职责：**
- **Interfaces/CLI**：接收用户输入，展示执行轨迹和最终结果
- **Application**：`AgentAppService` 编排 Agent 执行，转换 DTO
- **Domain/Agent**：Agent 端口、ReAct 循环逻辑、Conversation、上下文构建
- **Domain 端口**：`ChatModelPort`（LLM 调用）、`ToolRegistry`（工具执行）
- **Infrastructure**：DeepSeek HTTP 客户端、ToolRegistry 实现、工具适配器

### 1.4 技术选型汇总

| 技术项 | 选型 | 复用/新增 |
|--------|------|----------|
| LLM 调用端口 | `ChatModelPort` | 复用（M1 已完成） |
| LLM 实现 | `DeepSeekChatModel` | 复用（M1 已完成） |
| 工具注册表 | `ToolRegistry` 端口 + `ToolRegistryImpl` 实现 | 复用（第二次开发完成） |
| 工具执行上下文 | `ToolExecutionContext` | 复用（第二次开发完成） |
| 文本截断 | `TextTruncator` | 复用（第二次开发完成） |
| 工具参数解析 | `ToolArgumentParser` | 复用（第二次开发完成） |
| 消息模型 | `Message` 多态体系 | 复用（M1 已完成） |
| HTTP 客户端 | OkHttp（通过 DeepSeekHttpClient） | 复用（M1 已完成） |
| JSON 序列化 | Jackson ObjectMapper | 复用（M1 已完成） |
| 日志 | SLF4J + Logback | 复用（M1 已完成） |
| 会话管理 | `SessionAppService` + `AgentSession` | 复用（M1 已完成） |
| 任务状态机 | `AgentTask` 聚合根 | 复用（M1 已完成，状态机在 ReActLoop 中驱动） |

---

## 2. 需求分析

### 2.1 功能需求映射

| PRD 需求编号 | 功能需求 | 映射到的技术组件 |
|-------------|----------|-----------------|
| FR-6.1 | Agent 统一入口 | `Agent` 接口 + `DefaultAgent` + `AgentRequest` / `AgentResponse` |
| FR-6.2 | ReAct 有限循环 | `ReActLoop` 领域服务 |
| FR-6.3 | 执行步骤记录 | `ReActStep` 值对象 |
| FR-6.4 | 会话上下文管理 | `Conversation` 领域聚合 |
| FR-6.5 | 上下文构建 | `ContextBuildService` 领域服务 |
| FR-6.6 | LLM 响应解析 | `ReActLoop` 内联逻辑（Final Answer vs Tool Calls 判断） |
| FR-6.7 | 工具执行协调 | `ReActLoop` + `ToolRegistry.execute()` |
| FR-6.8 | 最大轮次控制 | `AgentTask` 状态机 + `ReActLoop` 循环条件判断 |
| FR-6.9 | 执行轨迹输出 | `ReActStep` 列表（通过 `AgentResponse.steps` 暴露） |

### 2.2 非功能需求

| 类别 | 需求 | 目标值 |
|------|------|--------|
| 性能 | 单轮 LLM 调用延迟 | 取决于 DeepSeek API，无额外开销 |
| 性能 | 工具执行延迟 | 取决于工具类型，Shell 工具 30s 超时 |
| 可靠性 | 工具失败不崩溃 | 所有工具异常捕获，转为失败 Observation |
| 可靠性 | LLM 请求失败不崩溃 | 捕获 ApiException，返回失败 AgentResponse |
| 安全 | 审批流程 | Shell 和写文件操作须经 ApprovalHandler 审批 |
| 可观测性 | 执行轨迹 | 每步 Thought/Action/Observation 结构化记录 |
| 可观测性 | 日志 | SLF4J，关键节点 info 级别，错误 warn/error |

### 2.3 约束条件

1. Java 17，Maven 构建
2. 不引入 LangChain、AutoGPT、Semantic Kernel 等第三方 Agent 框架
3. Agent 层只依赖端口接口（`ChatModelPort`、`ToolRegistry`），不依赖具体实现
4. Conversation 内存存储，不接入 MySQL/Redis
5. 同步调用（非流式），与现有 CLI 请求-响应模式一致
6. 所有领域对象不可变（immutable）+ Builder 模式
7. Default maxTurns = 8
8. Conversation 保留最近 20 条消息
9. 工具执行结果过长时截断

---

## 3. 总体技术架构

### 3.1 新增模块在 DDD 分层中的位置

```
src/main/java/com/flyagent/
  domain/
    agent/                          ← 新增：Agent 领域包
      Agent.java                    ← 新增：Agent 领域端口接口
      DefaultAgent.java             ← 新增：Agent 默认实现
      AgentRequest.java             ← 新增：Agent 请求值对象
      AgentResponse.java            ← 新增：Agent 响应值对象
      ReActLoop.java                ← 新增：ReAct 循环领域服务
      ReActStep.java                ← 新增：ReAct 执行步骤值对象
      Conversation.java             ← 新增：会话上下文领域聚合
      ContextBuildService.java      ← 新增：上下文构建领域服务
    chat/                           ← 已有：复用 ChatModelPort
    message/                        ← 已有：复用 Message 体系
    session/                        ← 已有：复用 AgentSession
    task/                           ← 已有：复用 AgentTask
    tool/                           ← 已有：复用 ToolRegistry / Tool / ToolResult
  application/
    service/
      AgentAppService.java          ← 变更：重构为委托 Agent 端口
  infrastructure/
    （无新增）
  interfaces/
    cli/
      AgentCli.java                 ← 变更：展示 ReAct 执行轨迹
```

### 3.2 模块依赖关系

```
DefaultAgent
  ├── depends on → ChatModelPort (domain port, 已有)
  ├── depends on → ToolRegistry (domain port, 已有)
  ├── depends on → AgentTask (domain aggregate, 已有)
  ├── creates → Conversation (domain aggregate, 新增)
  ├── creates → ContextBuildService (domain service, 新增)
  └── delegates to → ReActLoop (domain service, 新增)
                          ├── uses → ChatModelPort
                          ├── uses → ToolRegistry
                          ├── uses → Conversation
                          ├── uses → ContextBuildService
                          └── drives → AgentTask state machine

AgentAppService (application, 变更)
  └── depends on → Agent (domain port, 新增)
```

**依赖方向：全部向内指向领域层。** 新增代码不引入从领域层到基础设施层的依赖。

### 3.3 与现有模块的集成点

| 现有模块 | 集成方式 | 变更类型 |
|----------|---------|---------|
| `ChatModelPort` | 直接注入到 `ReActLoop`，每轮调用 `chat(ChatRequest)` | 无变更 |
| `ChatRequest` | `ContextBuildService.build()` 使用 Builder 模式构造 | 无变更 |
| `ChatResponse` / `ChatChoice` | `ReActLoop` 解析 `finishReason` 和 `toolCalls` | 无变更 |
| `ToolRegistry` | 直接注入到 `ReActLoop`，调用 `execute(ToolCall, ToolExecutionContext)` | 无变更 |
| `ToolResult.toToolMessage()` | `ContextBuildService` 使用该方法将工具结果转为 ToolMessage | 无变更 |
| `ToolDefinition` | `ContextBuildService` 通过 `ToolRegistry.definitions()` 获取工具列表 | 无变更 |
| `AgentTask` | `ReActLoop` 驱动其状态机（start → increaseTurn → complete/fail/hitMaxTurns） | 无变更 |
| `AgentSession` / `SessionAppService` | `DefaultAgent` 通过 sessionId 关联会话 | 无变更 |
| `AgentAppService` | 重构为注入 `Agent` 端口，委托执行 | 变更 |
| `AgentResponseDTO` | 新增从 `AgentResponse` 到 `AgentResponseDTO` 的映射方法 | 变更 |
| `AgentCli` | 新增 ReAct 执行轨迹展示逻辑 | 变更 |
| `Main.java` | 新增 Agent 端口 + 实现的组装和注入 | 变更 |

---

## 4. 领域模型设计

### 4.1 Agent 核心领域模型

#### 4.1.1 Agent 端口接口

```java
package com.flyagent.domain.agent;

/**
 * Agent 领域端口。
 *
 * <p>定义 Agent 执行的统一入口。领域层定义契约，
 * 不同 Agent 策略（ReAct、Plan-and-Execute）通过不同实现提供。</p>
 *
 * @author FlyAgent Team
 * @since 1.0
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

#### 4.1.2 AgentRequest 值对象

```java
package com.flyagent.domain.agent;

import java.nio.file.Path;

/**
 * Agent 请求值对象（不可变）。
 *
 * @author FlyAgent Team
 * @since 1.0
 */
public class AgentRequest {
    private final String sessionId;       // 会话 ID（可选）
    private final String userInput;       // 用户输入任务（必填）
    private final Path workspace;         // 工作目录（必填，必须存在）
    private final int maxTurns;           // 最大 ReAct 轮次（默认 8）

    // Builder 构造，build() 校验：
    // - userInput 不能为 null 或空白
    // - workspace 不能为 null 且必须存在
    // - maxTurns 默认 8，范围 [1, 100]
}
```

| 字段 | 类型 | 必填 | 默认值 | 校验规则 |
|------|------|------|--------|---------|
| `sessionId` | String | 否 | null | — |
| `userInput` | String | 是 | — | 不能为 null 或空白 |
| `workspace` | Path | 是 | — | 不能为 null；Files.isDirectory() == true |
| `maxTurns` | int | 否 | 8 | 范围 [1, 100] |

#### 4.1.3 AgentResponse 值对象

```java
package com.flyagent.domain.agent;

import java.util.List;

/**
 * Agent 响应值对象（不可变）。
 *
 * @author FlyAgent Team
 * @since 1.0
 */
public class AgentResponse {
    private final boolean success;            // 是否成功完成
    private final String finalAnswer;         // 最终回答（success=true 时）
    private final String error;               // 失败原因（success=false 时）
    private final List<ReActStep> steps;      // ReAct 执行轨迹
    private final int totalTurns;             // 总执行轮次

    // 工厂方法：success(finalAnswer, steps, totalTurns)
    // 工厂方法：error(error, steps, totalTurns)
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `success` | boolean | true=任务成功完成，false=异常终止 |
| `finalAnswer` | String | success=true 时有值 |
| `error` | String | success=false 时有值，包含人类可读的失败原因 |
| `steps` | List\<ReActStep\> | 完整的执行步骤列表，按时间顺序 |
| `totalTurns` | int | 已完成的总轮次数 |

#### 4.1.4 ReActStep 值对象

```java
package com.flyagent.domain.agent;

import java.util.Map;

/**
 * ReAct 执行步骤值对象（不可变）。
 *
 * <p>记录单轮 ReAct 循环的完整信息：Thought -> Action -> Observation，
 * 或 Final Answer（无需工具调用时的最终回复）。</p>
 *
 * @author FlyAgent Team
 * @since 1.0
 */
public class ReActStep {
    private final int turn;                    // 轮次序号（从 1 开始）
    private final String thought;              // 思考摘要（简短）
    private final String actionName;           // 工具名称（有工具调用时）
    private final Map<String, Object> actionArguments;  // 工具参数（有工具调用时）
    private final String observation;          // 工具执行结果（截断后）
    private final String finalAnswer;          // 最终回答（无工具调用时）
    private final boolean success;             // 本步骤是否执行成功
    private final String error;                // 本步骤的错误信息

    // Builder 构造
}
```

**ReActStep 的三种形态：**

| 形态 | thought | actionName | observation | finalAnswer | 触发条件 |
|------|---------|-----------|-------------|-------------|---------|
| Tool Call | 有 | 有 | 有 | null | LLM 返回 tool_calls |
| Final Answer | 有 | null | null | 有 | LLM 返回 content 且无 tool_calls |
| Error | 有 | null | null | null | LLM 失败 / 工具执行失败 |

#### 4.1.5 Conversation 领域聚合

```java
package com.flyagent.domain.agent;

import com.flyagent.domain.message.Message;
import com.flyagent.domain.tool.ToolCall;

import java.util.List;

/**
 * 会话上下文领域聚合。
 *
 * <p>维护 Agent 与 LLM 之间的完整对话历史，包括用户消息、助手消息和工具消息。
 * 内存存储，保留最近 N 条消息并自动截断过长的工具结果。</p>
 *
 * <p>消息窗口策略：始终保留 System Message 在首位，
 * 其后保留最近 (maxMessages - 1) 条非 System 消息。</p>
 *
 * @author FlyAgent Team
 * @since 1.0
 */
public class Conversation {
    /**
     * 添加用户消息。
     */
    public void addUserMessage(String content);

    /**
     * 添加助手纯文本消息（Final Answer 场景）。
     */
    public void addAssistantMessage(String content);

    /**
     * 添加助手工具调用消息（Tool Calls 场景）。
     */
    public void addAssistantToolCalls(List<ToolCall> toolCalls);

    /**
     * 添加工具执行结果消息（Observation）。
     * 会自动截断超过 maxToolResultChars 的结果。
     */
    public void addToolMessage(String toolCallId, String content);

    /**
     * 获取最近的对话消息（用于构建 ChatRequest.messages）。
     * 返回不可变视图，System Message 始终在首位。
     *
     * @return 最多 maxMessages 条消息
     */
    public List<Message> getMessages();

    /**
     * 清空所有非 System 消息。
     */
    public void clear();

    /**
     * 获取当前消息总数（含 System Message）。
     */
    public int size();
}
```

**Conversation 构造参数：**

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `maxMessages` | 20 | 最大保留消息数（含 System Message） |
| `maxToolResultChars` | 4000 | 单条工具结果最大字符数 |
| `systemPrompt` | — | 系统提示词（构造时注入，不可变） |

#### 4.1.6 ContextBuildService 领域服务

```java
package com.flyagent.domain.agent;

import com.flyagent.domain.chat.ChatRequest;
import com.flyagent.domain.message.Message;
import com.flyagent.domain.tool.ToolDefinition;

import java.nio.file.Path;
import java.util.List;

/**
 * 上下文构建领域服务。
 *
 * <p>负责组装发送给 LLM 的完整请求上下文，包括：
 * System Prompt、工具定义列表、历史对话消息。</p>
 *
 * @author FlyAgent Team
 * @since 1.0
 */
public class ContextBuildService {

    /**
     * 构建 ChatRequest。
     *
     * @param conversation 当前会话上下文
     * @param toolDefinitions 可用工具定义列表
     * @param workspacePath 当前工作目录路径
     * @return 完整 ChatRequest
     */
    public ChatRequest build(
            Conversation conversation,
            List<ToolDefinition> toolDefinitions,
            Path workspacePath);
}
```

### 4.2 ReActLoop 领域服务

```java
package com.flyagent.domain.agent;

import com.flyagent.domain.chat.ChatModelPort;
import com.flyagent.domain.task.AgentTask;
import com.flyagent.domain.tool.ToolExecutionContext;
import com.flyagent.domain.tool.ToolRegistry;

/**
 * ReAct 循环领域服务。
 *
 * <p>实现标准的 ReAct (Reasoning + Acting) 循环：
 * Thought → Action → Observation 的迭代执行，
 * 直到获得 Final Answer 或触发终止条件。</p>
 *
 * <p>不直接依赖任何基础设施实现，所有外部能力通过端口注入。</p>
 *
 * @author FlyAgent Team
 * @since 1.0
 */
public class ReActLoop {
    private final ChatModelPort chatModel;
    private final ToolRegistry toolRegistry;

    public ReActLoop(ChatModelPort chatModel, ToolRegistry toolRegistry);

    /**
     * 执行 ReAct 循环。
     *
     * @param agentRequest     用户请求
     * @param toolContext      工具执行上下文
     * @param contextBuilder   上下文构建服务
     * @param conversation     会话上下文
     * @param task             任务聚合根（用于状态机管理）
     * @return 完整 AgentResponse
     */
    public AgentResponse execute(
            AgentRequest agentRequest,
            ToolExecutionContext toolContext,
            ContextBuildService contextBuilder,
            Conversation conversation,
            AgentTask task);
}
```

### 4.3 与现有领域模型的关系图

```
┌─────────────────────────────────────────────────────────────┐
│ Domain Layer                                                 │
│                                                              │
│  ┌──────────┐    ┌──────────┐    ┌──────────────────┐       │
│  │ AgentSession│  │ AgentTask │    │  Conversation    │       │
│  │ (已有)     │   │ (已有)    │    │  (新增)          │       │
│  │            │   │           │    │                  │       │
│  │ - sessionId│   │ - taskId  │    │ - messages[]     │       │
│  │ - workspace│   │ - status  │    │ - maxMessages    │       │
│  │ - status   │   │ - maxTurns│    │ - systemPrompt   │       │
│  │            │   │ - currTurn│    │                  │       │
│  │ 会话生命周期│   │ - finalAns│    │ 对话历史管理     │       │
│  └──────────┘    └──────────┘    └──────────────────┘       │
│        │               │                   │                │
│        │  1:N          │ 驱动状态机        │ 读写            │
│        v               v                   v                │
│  ┌──────────────────────────────────────────────────────┐   │
│  │                    ReActLoop (新增)                    │   │
│  │  - chatModel: ChatModelPort                          │   │
│  │  - toolRegistry: ToolRegistry                        │   │
│  │                                                      │   │
│  │  execute(request, context, builder, conv, task)       │   │
│  └──────────────────────────────────────────────────────┘   │
│        │                    │                               │
│        v                    v                               │
│  ┌──────────────┐    ┌──────────────┐                      │
│  │ ChatModelPort│    │ ToolRegistry │                      │
│  │ (已有)       │    │ (已有)       │                      │
│  └──────────────┘    └──────────────┘                      │
│                                                             │
│  ┌──────────────────────┐   ┌──────────────────────┐        │
│  │ ContextBuildService  │   │ DefaultAgent         │        │
│  │ (新增)               │   │ (新增)               │        │
│  │                      │   │ implements Agent     │        │
│  │ build(conv, tools,   │   └──────────────────────┘        │
│  │   workspace) -> ChatRequest                             │
│  └──────────────────────┘                                   │
└─────────────────────────────────────────────────────────────┘
```

**职责边界说明：**

| 对象 | 职责 | 生命周期 |
|------|------|---------|
| `AgentSession` | 管理会话创建/关闭，关联 workspace | 跨多次任务调用 |
| `AgentTask` | 管理单次任务的执行状态和轮次计数 | 单次 Agent.run() |
| `Conversation` | 管理对话消息历史和上下文窗口 | 跨多次任务调用（同会话内） |
| `ReActLoop` | 执行 ReAct 算法逻辑，驱动任务状态机 | 单次 Agent.run() |

### 4.4 CodingChatModel 端口的设计决策

**决策：不新建 `CodingChatModel` 端口，直接复用 `ChatModelPort`。**

**详细分析：**

PRD 中提到了 `CodingChatModel` 端口概念，建议 Agent 层通过该端口调用 LLM。但现有 `ChatModelPort` 已经完全具备以下能力：

| 需要的功能 | ChatModelPort 是否支持 |
|-----------|----------------------|
| 发送消息列表（含 system/user/assistant/tool） | ChatRequest.messages: List\<Message\> |
| 注入工具定义列表 | ChatRequest.tools: List\<ToolDefinition\> |
| 控制 thinking 模式 | ChatRequest.thinking: boolean |
| 同步调用 | chat() 方法是同步的，返回 ChatResponse |
| 获取 tool_calls | ChatResponse.firstChoice().getMessage().getToolCalls() |
| 获取 finish_reason | ChatResponse.firstChoice().getFinishReason() |

**不需要新端口的原因：**
1. 不存在 ChatModelPort 无法满足的功能需求
2. `CodingChatModel` 和 `ChatModelPort` 接口签名会高度重合，导致不必要的抽象
3. Agent 层对 LLM 没有额外的抽象需求——它就是发送消息、获取回复
4. 如果未来需要支持特定于代码场景的 LLM 能力（如代码补全、diff 生成），可以在 **ChatRequest** 上扩展字段，而非创建新端口

**遵循的原则：**
- Agent 层只依赖端口接口（`ChatModelPort`），不依赖具体实现（`DeepSeekChatModel`）—— 满足
- 保持架构简洁，避免重复抽象 —— 满足

---

## 5. 核心流程设计

### 5.1 ReAct 循环整体流程

```
                    ┌─────────────┐
                    │ Agent.run() │
                    └──────┬──────┘
                           │
                           v
                    ┌──────────────┐
                    │ 创建 AgentTask │
                    │ task.start()  │
                    └──────┬───────┘
                           │
                           v
                    ┌──────────────┐
                    │  添加 User    │
                    │  Message 到   │
                    │  Conversation │
                    └──────┬───────┘
                           │
              ┌────────────┴────────────┐
              │                         │
              v                         v
    ┌─────────────────┐         ┌──────────────┐
    │ 还能继续执行？   │──否──→  │ 返回失败      │
    │ canContinue()   │         │ HIT_MAX_TURNS │
    └────────┬────────┘         └──────────────┘
             │ 是
             v
    ┌──────────────────┐
    │ ContextBuildService│
    │ .build()          │
    │ → ChatRequest     │
    └────────┬─────────┘
             │
             v
    ┌──────────────────┐
    │ chatModel.chat()  │
    │ → ChatResponse    │
    └────────┬─────────┘
             │
    ┌────────┴────────┐
    │                 │
    v                 v
  ┌───────────┐  ┌────────────┐
  │ API 失败   │  │ API 成功   │
  │ 或空响应   │  │            │
  └─────┬─────┘  └─────┬──────┘
        │              │
        v              v
  ┌──────────┐  ┌──────────────┐
  │ 返回失败  │  │ 解析响应     │
  │ AgentResp│  │ finishReason │
  └──────────┘  │ + toolCalls  │
                └──────┬───────┘
                       │
            ┌──────────┼──────────┐
            │          │          │
            v          v          v
      ┌─────────┐ ┌─────────┐ ┌─────────┐
      │stop/    │ │tool_calls│ │空响应   │
      │content  │ │         │ │         │
      │非空     │ │         │ │         │
      └────┬────┘ └────┬────┘ └────┬────┘
           │           │           │
           v           v           v
    ┌────────────┐ ┌───────────┐ ┌──────────┐
    │ Final      │ │ 逐个执行  │ │ 返回失败 │
    │ Answer     │ │ 工具调用  │ │          │
    │ 记录 step  │ └─────┬─────┘ └──────────┘
    │ task.complete│    │
    └────────────┘     v
                 ┌───────────────┐
                 │ ToolRegistry  │
                 │ .execute()    │
                 │ → ToolResult  │
                 └───────┬───────┘
                         │
                ┌────────┴────────┐
                │                 │
                v                 v
          ┌──────────┐    ┌──────────────┐
          │ 成功     │    │ 失败/未知工具 │
          │ Observation│  │ 错误          │
          │           │    │ Observation   │
          └─────┬─────┘    └──────┬───────┘
                │                 │
                └────────┬────────┘
                         │
                         v
                  ┌──────────────┐
                  │ 写入         │
                  │ Conversation │
                  │ 记录 ReActStep│
                  │ task.increaseTurn│
                  └──────┬───────┘
                         │
                         └──→ 循环回到 "还能继续执行？"
```

### 5.2 LLM 响应解析逻辑

```java
// ReActLoop 内部的响应解析伪代码

ChatResponse response = chatModel.chat(request);
ChatChoice choice = response.firstChoice();

if (choice == null) {
    // 空响应：记录错误 step，返回失败
    recordErrorStep("Model returned empty response");
    return AgentResponse.error("No response from model", steps, currentTurn);
}

AssistantMessage msg = choice.getMessage();
String finishReason = choice.getFinishReason();

// 情况 1：content 非空 + 无 tool_calls → Final Answer
if (!msg.hasToolCalls() && msg.hasContent()
        && !"tool_calls".equals(finishReason)) {
    recordFinalAnswerStep(choice);
    task.complete(content);
    return AgentResponse.success(content, steps, currentTurn);
}

// 情况 2：有 tool_calls → 进入工具执行
if (msg.hasToolCalls()) {
    // 记录 thought（取 content 的前 200 字符作为思考摘要）
    String thought = msg.hasContent()
            ? msg.getContent().substring(0, Math.min(200, msg.getContent().length()))
            : "Calling tools...";

    // 逐个执行工具调用
    for (ToolCall toolCall : msg.getToolCalls()) {
        ToolResult result = toolRegistry.execute(toolCall, toolContext);
        ReActStep step = buildReActStep(thought, toolCall, result);
        steps.add(step);

        // Observation 写入 Conversation
        conversation.addToolMessage(
                toolCall.getId(),
                result.isSuccess() ? result.getContent() : "Error: " + result.getError()
        );
    }
    task.increaseTurn();
    // → 继续下一轮循环
}

// 情况 3：content 空 + 无 tool_calls → 异常响应
recordErrorStep("Model returned neither content nor tool calls");
return AgentResponse.error("Model returned empty response", steps, currentTurn);
```

### 5.3 工具执行协调流程

```
对每个 ToolCall：
  1. 获取工具名称: toolCall.getFunction().getName()
  2. 查找工具: toolRegistry.find(name)
  3. 如果未找到:
     - 生成失败 Observation: "Unknown tool: {name}"
     - 记录 ReActStep (success=false, error="Unknown tool")
     - 写入 conversation.addToolMessage(id, "Error: Unknown tool: {name}")
     - 继续执行下一个 ToolCall
  4. 如果找到:
     - 调用 toolRegistry.execute(toolCall, toolContext)
     - ToolRegistry 内部会处理：审批检查 → 安全检查 → 执行 → 异常捕获
     - 根据 ToolResult.isSuccess():
       - 成功: Observation = result.getContent() (截断后)
       - 失败: Observation = "Error: " + result.getError()
  5. 记录 ReActStep (含 thought, actionName, actionArguments, observation)
  6. 写入 conversation.addToolMessage(toolCallId, observation)
```

**多工具顺序执行：** 按 DeepSeek 返回的 tool_calls 数组顺序依次执行。每个工具的结果独立写入 Conversation，下一轮 LLM 可以看到所有 Observation。

### 5.4 上下文构建流程

```
ContextBuildService.build(conversation, toolDefinitions, workspacePath):

  1. 获取 System Prompt
     - 包含 Agent 身份、ReAct 指令、workspace 信息
     - 格式见下文 System Prompt 模板

  2. 获取历史消息
     - conversation.getMessages()
     - System Message 始终在首位
     - 后面紧跟最近 N 条对话消息

  3. 获取工具定义
     - toolDefinitions (从 toolRegistry.definitions() 获取)

  4. 组装 ChatRequest
     ChatRequest.builder()
         .messages(messages)   // System + history
         .tools(toolDefinitions)
         .thinking(true)       // 开启深度思考
         .reasoningEffort("high")
         .build()
```

### 5.5 System Prompt 模板

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
5. If you need to read a file, use read_file. If you need to explore a directory, use list_files.
```

### 5.6 最大轮次控制

```java
// 循环入口条件检查
while (task.canContinue()) {   // status == RUNNING && currentTurn < maxTurns
    // ... 执行一轮 ReAct ...
}

// 循环结束后的判断
if (task.getStatus() == TaskStatus.RUNNING) {
    // 达到最大轮次但未完成
    task.hitMaxTurns();
    return AgentResponse.error(
        "Reached maximum turns (" + maxTurns + ") without completing the task. " +
        "Last observation: " + getLastObservation(steps),
        steps,
        task.getCurrentTurn()
    );
}
```

**AgentTask 状态机驱动：**

```
CREATED ──start()──→ RUNNING ──complete()──→ COMPLETED
                        │
                        ├──fail()──→ FAILED
                        │
                        └──hitMaxTurns()──→ HIT_MAX_TURNS
```

ReActLoop 在以下时间点操作 AgentTask 状态：
- 循环开始前：`task.start()` → CREATED → RUNNING
- 每轮结束后：`task.increaseTurn()` → currentTurn++
- Final Answer 时：`task.complete(content)` → COMPLETED
- LLM 调用失败时：`task.fail(reason)` → FAILED
- 超过 maxTurns 时：`task.hitMaxTurns()` → HIT_MAX_TURNS

---

## 6. 模块详细设计

### 6.1 Agent 接口 + DefaultAgent

**文件位置：**
- `src/main/java/com/flyagent/domain/agent/Agent.java`
- `src/main/java/com/flyagent/domain/agent/DefaultAgent.java`

**DefaultAgent 职责：**
- 接收 `AgentRequest`，完成输入校验
- 创建或获取 `Conversation`
- 创建 `AgentTask`（通过 `AgentTask(SessionId, userInstruction, maxTurns)`）
- 创建 `ContextBuildService`
- 构建 `ToolExecutionContext`
- 委托 `ReActLoop.execute()` 执行
- 返回 `AgentResponse`

**关键逻辑：**

```java
public class DefaultAgent implements Agent {
    private final ChatModelPort chatModel;
    private final ToolRegistry toolRegistry;
    private final SessionAppService sessionService;

    @Override
    public AgentResponse run(AgentRequest request) {
        // 1. 校验输入
        validateRequest(request);

        // 2. 获取或创建会话
        AgentSession session = getOrCreateSession(request.getSessionId());

        // 3. 创建任务
        AgentTask task = new AgentTask(
                session.getId(),
                request.getUserInput(),
                request.getMaxTurns()
        );

        // 4. 创建 Conversation
        Conversation conversation = new Conversation(
                20,     // maxMessages
                4000    // maxToolResultChars
        );
        conversation.addUserMessage(request.getUserInput());

        // 5. 创建上下文构建服务
        ContextBuildService contextBuilder = new ContextBuildService();

        // 6. 构建工具执行上下文
        ToolExecutionContext toolContext = ToolExecutionContext
                .builder(request.getWorkspace())
                .approvalHandler(approvalHandler)
                .workspaceGuard(new WorkspaceGuard(request.getWorkspace()))
                .build();

        // 7. 创建 ReActLoop 并执行
        ReActLoop loop = new ReActLoop(chatModel, toolRegistry);
        return loop.execute(request, toolContext, contextBuilder, conversation, task);
    }
}
```

### 6.2 ReActLoop

**文件位置：** `src/main/java/com/flyagent/domain/agent/ReActLoop.java`

**关键实现要点：**

| 要点 | 实现 |
|------|------|
| 循环控制 | `while (task.canContinue())` — 状态 RUNNING + currentTurn < maxTurns |
| LLM 调用 | 每轮调用 `chatModel.chat()`，捕获 `ApiException` |
| 响应解析 | 通过 `ChatChoice.getFinishReason()` + `getMessage().hasToolCalls()` 判断 |
| 工具执行 | 逐个遍历 `toolCalls`，调用 `toolRegistry.execute()` |
| 步骤记录 | 每步创建 `ReActStep`，追加到 steps 列表 |
| 状态驱动 | 在每个关键节点调用 `task.start/complete/fail/hitMaxTurns/increaseTurn` |

**伪代码骨架：**

```java
public AgentResponse execute(
        AgentRequest request, ToolExecutionContext toolContext,
        ContextBuildService contextBuilder, Conversation conversation,
        AgentTask task) {

    task.start();
    List<ReActStep> steps = new ArrayList<>();
    List<ToolDefinition> toolDefs = toolRegistry.definitions();

    while (task.canContinue()) {
        // 1. 构建请求
        ChatRequest chatRequest = contextBuilder.build(
                conversation, toolDefs, request.getWorkspace());

        // 2. 调用 LLM
        ChatResponse response;
        try {
            response = chatModel.chat(chatRequest);
        } catch (ApiException e) {
            task.fail("LLM request failed: " + e.getMessage());
            return AgentResponse.error(e.getMessage(), steps, task.getCurrentTurn());
        }

        // 3. 解析响应
        ChatChoice choice = response.firstChoice();
        if (choice == null) {
            task.fail("Model returned empty response");
            return AgentResponse.error("Model returned empty response", steps, task.getCurrentTurn());
        }

        AssistantMessage msg = choice.getMessage();

        // 4. 情况 A: Final Answer
        if (!msg.hasToolCalls() && msg.hasContent()
                && !"tool_calls".equals(choice.getFinishReason())) {
            ReActStep step = ReActStep.builder()
                    .turn(task.getCurrentTurn() + 1)
                    .thought(extractThought(msg))
                    .finalAnswer(msg.getContent())
                    .success(true)
                    .build();
            steps.add(step);
            conversation.addAssistantMessage(msg.getContent());
            task.complete(msg.getContent());
            return AgentResponse.success(msg.getContent(), steps, task.getCurrentTurn() + 1);
        }

        // 5. 情况 B: Tool Calls
        if (msg.hasToolCalls()) {
            String thought = extractThought(msg);

            // 添加 assistant tool call message 到 conversation
            conversation.addAssistantToolCalls(msg.getToolCalls());

            for (ToolCall tc : msg.getToolCalls()) {
                ToolResult result = toolRegistry.execute(tc, toolContext);

                ReActStep step = ReActStep.builder()
                        .turn(task.getCurrentTurn() + 1)
                        .thought(thought)
                        .actionName(tc.getFunction().getName())
                        .actionArguments(parseArguments(tc.getFunction().getArguments()))
                        .observation(buildObservation(result))
                        .success(result.isSuccess())
                        .error(result.isSuccess() ? null : result.getError())
                        .build();
                steps.add(step);

                // 写入 Observation 到 Conversation
                String toolContent = result.isSuccess()
                        ? result.getContent()
                        : "Error: " + result.getError();
                conversation.addToolMessage(tc.getId(), toolContent);
            }

            task.increaseTurn();
            continue;
        }

        // 6. 情况 C: 既无 content 也无 tool_calls
        task.fail("Model returned neither content nor tool calls");
        return AgentResponse.error("Model returned empty response", steps, task.getCurrentTurn());
    }

    // 循环结束：达到最大轮次
    task.hitMaxTurns();
    return AgentResponse.error(
            "Reached maximum turns (" + request.getMaxTurns()
            + ") without completing the task.",
            steps, task.getCurrentTurn());
}
```

### 6.3 Conversation

**文件位置：** `src/main/java/com/flyagent/domain/agent/Conversation.java`

**内部数据结构：**

```java
public class Conversation {
    private final SystemMessage systemMessage;          // 不可变，始终保留
    private final Deque<Message> historyMessages;      // 非 System 消息队列
    private final int maxMessages;                      // 最大消息数
    private final int maxToolResultChars;              // 工具结果最大字符数
}
```

**截断策略：**
1. **工具结果截断**：`addToolMessage()` 时，若 content 长度超过 `maxToolResultChars`，截断并附加 `[... truncated ...]` 标记。可用现有 `TextTruncator.truncate()`。
2. **消息窗口截断**：`getMessages()` 时，仅返回 System Message + 最近 `(maxMessages - 1)` 条历史消息。历史超过窗口时，最旧的非 System 消息被丢弃。

**线程安全：** `Conversation` 不保证线程安全。在当前同步 CLI 场景下，单线程访问足够。

### 6.4 ContextBuildService

**文件位置：** `src/main/java/com/flyagent/domain/agent/ContextBuildService.java`

**System Prompt 生成逻辑：**

```java
public ChatRequest build(
        Conversation conversation,
        List<ToolDefinition> toolDefinitions,
        Path workspacePath) {

    // 1. 获取或生成 System Prompt
    String systemPrompt = buildSystemPrompt(workspacePath);

    // 2. 获取消息列表（System Message 始终在最前）
    List<Message> messages = new ArrayList<>();
    messages.add(new SystemMessage(systemPrompt));
    messages.addAll(conversation.getHistoryMessages()); // 不含 System Message

    // 3. 构建 ChatRequest
    return ChatRequest.builder()
            .messages(messages)
            .tools(toolDefinitions)
            .thinking(true)
            .stream(false)
            .build();
}

private String buildSystemPrompt(Path workspacePath) {
    return String.format("""
            You are FlyAgent, a CLI coding agent running on the user's local machine.

            You operate in a ReAct (Reasoning + Acting) loop:
            - **Thought**: Briefly explain your next step.
            - **Action**: Call a tool when you need to read files, list directories,
              run commands, or modify the project.
            - **Observation**: The tool result will be provided. Never fabricate
              observations — only use actual tool outputs.
            - **Final Answer**: Provide your final response when the task is complete.

            Current workspace: %s
            Current date: %s

            Important rules:
            1. Always use tools to gather information — do not guess.
            2. If a tool fails, report the error and try an alternative approach.
            3. Stop when the task is complete and provide a concise Final Answer.
            """,
            workspacePath.toAbsolutePath(),
            LocalDate.now().toString());
}
```

### 6.5 AgentRequest 校验逻辑

| 校验项 | 条件 | 异常 |
|--------|------|------|
| userInput | `null` 或 `isBlank()` | `IllegalArgumentException("userInput must not be empty")` |
| workspace | `null` | `IllegalArgumentException("workspace must not be null")` |
| workspace | `Files.isDirectory(workspace) == false` | `IllegalArgumentException("workspace does not exist or is not a directory: " + workspace)` |
| maxTurns | < 1 或 > 100 | 自动 clamp 到 [1, 100] 范围，并记录 warn 日志 |

---

## 7. 与现有系统的集成

### 7.1 复用 ChatModelPort

**集成方式：** 构造器注入。`ReActLoop` 通过 `ChatModelPort` 调用模型，完全不感知 DeepSeek HTTP 细节。

```java
// Main.java 组装（变更）
ChatModelPort chatModel = new DeepSeekChatModel(dsConfig, objectMapper);

// 新增：Agent 组装
ReActLoop reActLoop = new ReActLoop(chatModel, toolRegistry);
DefaultAgent agent = new DefaultAgent(chatModel, toolRegistry, sessionService, approvalHandler);

// 更新 AgentAppService
AgentAppService agentService = new AgentAppService(agent, sessionService);
```

### 7.2 复用 ToolRegistry

**集成方式：** 构造器注入。`ReActLoop` 通过 `ToolRegistry` 接口执行工具，不直接依赖具体工具类。

```java
// 工具注册（Main.java，不变）
ToolRegistry toolRegistry = new ToolRegistryImpl();
toolRegistry.registerAll(
    new ReadFileTool(),
    new WriteFileTool(),
    new ListFilesTool(),
    new RunShellTool(),
    new CreateProjectStructureTool()
);

// 工具定义注入到 LLM 请求（ReActLoop 中）
List<ToolDefinition> toolDefs = toolRegistry.definitions();
// 传递给 ContextBuildService.build()
```

### 7.3 复用 ToolResult.toToolMessage()

**集成方式：** `Conversation.addToolMessage()` 内部调用，或由 `ContextBuildService` 在构建消息列表时使用。

实际上，`Conversation` 直接存储 `Message` 对象（包括 `ToolMessage`），而 `ToolMessage` 需要 toolCallId 和 content 两个字段。流程是：

```java
// 在 ReActLoop 中：
ToolResult result = toolRegistry.execute(toolCall, toolContext);
// 方式 A：使用 ToolResult.toToolMessage()
ToolMessage toolMessage = result.toToolMessage(toolCall.getId());
conversation.addMessage(toolMessage);

// 方式 B：Conversation 内部处理
conversation.addToolMessage(toolCall.getId(), result.isSuccess()
        ? result.getContent() : "Error: " + result.getError());
```

**推荐方式 B：** `Conversation.addToolMessage()` 封装细节，同时处理截断逻辑。`ToolResult.toToolMessage()` 保留供其他场景使用。

### 7.4 扩展/重构 AgentAppService

**现状：** `AgentAppService.handleUserInput(String)` 直接调用 `chatModel.chat()`，单轮对话。

**目标：** `AgentAppService` 委托给 `Agent` 端口，支持多轮 ReAct。

```java
public class AgentAppService {
    private final Agent agent;                    // 新增：注入 Agent 端口
    private final SessionAppService sessionService; // 已有

    public AgentAppService(Agent agent, SessionAppService sessionService) {
        this.agent = agent;
        this.sessionService = sessionService;
    }

    /**
     * 处理用户输入（ReAct 多轮对话）。
     */
    public AgentResponseDTO handleUserInput(String userInput) {
        if (!sessionService.hasActiveSession()) {
            return AgentResponseDTO.error("No active session. Please start a session first.");
        }

        AgentSession session = sessionService.getCurrentSession();

        // 构建 AgentRequest
        AgentRequest request = AgentRequest.builder()
                .sessionId(session.getId().getValue())
                .userInput(userInput)
                .workspace(session.getWorkspace().getPath())
                .maxTurns(8)  // 默认值
                .build();

        // 委托 Agent 执行
        AgentResponse agentResponse = agent.run(request);

        // 转换 DTO
        return AgentResponseDTO.from(agentResponse);
    }
}
```

**AgentResponseDTO 扩展：**

```java
public class AgentResponseDTO {
    // 已有字段：success, content, hasToolCalls, tokenUsage, errorMessage
    // 新增字段：
    private final List<ReActStepDTO> steps;  // ReAct 执行轨迹（精简后给 CLI 展示）
    private final int totalTurns;             // 总轮次数
}
```

### 7.5 CLI 展示层变更

`AgentCli` 需要在展示 Agent 回复时，同时展示 ReAct 执行轨迹。展示格式：

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
  It uses DDD + Hexagonal Architecture with the following modules...
```

### 7.6 Main.java 变更汇总

| 变更点 | 说明 |
|--------|------|
| 新增 import | `DefaultAgent`, `ReActLoop`, `Agent` |
| Agent 组装 | `new DefaultAgent(chatModel, toolRegistry, sessionService, approvalHandler)` |
| AgentAppService 构造 | `new AgentAppService(agent, sessionService)` |
| 移除 TODO | `// TODO: Inject toolRegistry and toolContext into AgentAppService...` |

---

## 8. 数据流与序列化

### 8.1 端到端数据流

```
User Input (String)
    │
    ▼
AgentRequest (domain value object)
    │
    ▼
DefaultAgent.run()
    │
    ├── Conversation.addUserMessage(userInput)
    │       └── 内部: userInput → new UserMessage(content)
    │
    ▼
ReActLoop.execute()
    │
    ├── [每轮循环]
    │   ├── ContextBuildService.build(conversation, toolDefs, workspace)
    │   │       └── 输出: ChatRequest {
    │   │               messages: [SystemMessage, ...历史消息],
    │   │               tools: [ToolDefinition...]
    │   │           }
    │   │
    │   ├── chatModel.chat(chatRequest)
    │   │       └── 输出: ChatResponse {
    │   │               choices: [ChatChoice {
    │   │                   message: AssistantMessage,
    │   │                   finishReason: "stop" | "tool_calls"
    │   │               }]
    │   │           }
    │   │
    │   ├── [如果 tool_calls]
    │   │   ├── conversation.addAssistantToolCalls(toolCalls)
    │   │   │
    │   │   └── for each ToolCall:
    │   │       ├── toolRegistry.execute(toolCall, toolContext)
    │   │       │       └── 输出: ToolResult { success, content, error }
    │   │       │
    │   │       ├── ReActStep {
    │   │       │       turn, thought, actionName,
    │   │       │       actionArguments, observation,
    │   │       │       success, error
    │   │       │   }
    │   │       │
    │   │       └── conversation.addToolMessage(toolCallId, observation)
    │   │
    │   └── [如果 Final Answer]
    │       ├── conversation.addAssistantMessage(content)
    │       └── break loop
    │
    ▼
AgentResponse (domain value object)
    │
    ▼
AgentResponseDTO (application DTO)
    │
    ▼
CLI Display
```

### 8.2 Conversation 消息管理

**消息顺序示例（3 轮对话后的 Conversation 内部状态）：**

```
[0] SystemMessage:     "You are FlyAgent, a CLI coding agent..."
[1] UserMessage:       "帮我分析这个项目结构"
[2] AssistantMessage:  (toolCalls: [list_files, read_file])  ← 第 1 轮
[3] ToolMessage:       (toolCallId=call_1, content="Found pom.xml, src/...")
[4] ToolMessage:       (toolCallId=call_2, content="This is a Java Maven project...")
[5] AssistantMessage:  (toolCalls: [read_file])              ← 第 2 轮
[6] ToolMessage:       (toolCallId=call_3, content="<project>...")
[7] AssistantMessage:  (content="This project is FlyAgent...") ← Final Answer
```

**消息超过 20 条时的滑动窗口行为：**

```
[0] SystemMessage:     (始终保留)
[1] UserMessage:       (第 N-18 条)
...
[19] AssistantMessage: (最新一条)
```

最旧的消息（索引 1）被丢弃，新消息追加到末尾。

### 8.3 工具结果截断策略

| 工具 | 典型输出长度 | 截断阈值 | 截断标记 |
|------|------------|---------|---------|
| read_file | 可能很大（文件内容） | 4000 字符 | `[... truncated at 4000 chars, total 15000 chars]` |
| list_files | 通常 < 2000 | 4000 字符 | 一般不需要截断 |
| run_shell | 取决于命令 | 4000 字符 | `[... truncated at 4000 chars, total 8000 chars]` |
| write_file | 确认信息，很短 | 4000 字符 | 一般不需要截断 |
| create_project_structure | 确认信息，很短 | 4000 字符 | 一般不需要截断 |

**实现：** `Conversation.addToolMessage()` 内部调用 `TextTruncator.truncate(content, maxToolResultChars, "Tool result")`。

---

## 9. 异常处理策略

### 9.1 异常处理矩阵

| 场景 | 异常类型 | 处理位置 | 处理方式 | 对 Agent 的影响 |
|------|---------|---------|---------|----------------|
| userInput 为空 | `IllegalArgumentException` | `DefaultAgent` | 立即抛出，不启动 Agent | 调用方捕获 |
| workspace 不存在 | `IllegalArgumentException` | `DefaultAgent` | 立即抛出，不启动 Agent | 调用方捕获 |
| LLM API 请求失败 | `ApiException` | `ReActLoop` | 捕获，`task.fail()`，返回 `AgentResponse.error()` | 终止执行，保留已执行 steps |
| LLM 返回空 choices | — | `ReActLoop` | `task.fail()`，返回 `AgentResponse.error()` | 终止执行 |
| 工具未注册 | — | `ToolRegistryImpl.execute()` | 返回 `ToolResult.error("Unknown tool: ...")` | 生成失败 Observation，继续下一轮 |
| 工具参数解析失败 | `ToolExecutionException` | `ToolRegistryImpl.execute()` | 返回 `ToolResult.error()` | 生成失败 Observation，继续下一轮 |
| 工具执行时抛出异常 | 各种 | `ToolRegistryImpl.execute()` (兜底) | 捕获，返回 `ToolResult.error()` | 生成失败 Observation，继续下一轮 |
| 安全违规 | `SecurityException` | `ToolRegistryImpl.execute()` | 返回 `ToolResult.error()` (带 security_violation 标记) | 生成失败 Observation，继续下一轮 |
| 达到 maxTurns | — | `ReActLoop` | `task.hitMaxTurns()`，返回 `AgentResponse.error()` | 终止执行，返回已完成 steps |
| 用户拒绝审批 | — | `ApprovalHandler` 返回 false | 返回 `ToolResult.error("Approval denied")` | 生成拒绝 Observation，模型可在下一轮调整策略 |

### 9.2 分层错误处理原则

```
┌─────────────────────────────────────────────┐
│ Domain Layer (ReActLoop)                     │
│ - 捕获 ApiException → 终止执行               │
│ - 解析 ToolResult.isSuccess() → 决定是否继续 │
│ - 不捕获 RuntimeException (让其传播到 CLI)   │
└─────────────────────────────────────────────┘
        │
        ▼
┌─────────────────────────────────────────────┐
│ Infrastructure Layer (ToolRegistryImpl)       │
│ - 捕获 SecurityException → ToolResult.error  │
│ - 捕获所有 Exception → ToolResult.error      │
│ - 绝不抛出非受检异常给上层                    │
└─────────────────────────────────────────────┘
```

### 9.3 错误 Observation 格式

工具执行失败时的 Observation 格式（写入 Conversation）：

```
Error: {errorMessage}
```

示例：
- 工具不存在：`Error: Unknown tool: unknown_tool_name`
- 文件不存在：`Error: File not found: /path/to/file`
- Shell 命令失败：`Error: Command failed with exit code 1: error output...`
- 安全违规：`Error: Access denied: path is outside workspace`
- 审批拒绝：`Error: Approval denied for action: run_shell mvn clean install`

---

## 10. 测试策略

### 10.1 测试金字塔

```
           ┌──────────┐
           │ E2E Tests│  1-2 个：完整 Agent.run() 流程
           │ (集成)   │
           ├──────────┤
           │ Component│  3-4 个：ReActLoop + Mock LLM + Mock ToolRegistry
           │  Tests   │
           ├──────────┤
           │  Unit    │  15+ 个：每个类 / 方法的独立测试
           │  Tests   │
           └──────────┘
```

### 10.2 单元测试用例

#### AgentRequest 测试

| 用例 | 输入 | 期望 |
|------|------|------|
| 正常构造 | userInput="test", workspace=/tmp | 构造成功 |
| 空 userInput | userInput="" | IllegalArgumentException |
| null workspace | workspace=null | IllegalArgumentException |
| workspace 不存在 | workspace="/nonexistent" | IllegalArgumentException |
| maxTurns 默认值 | 不设置 maxTurns | maxTurns == 8 |
| maxTurns 超出范围 | maxTurns=200 | clamp 到 100 或抛异常 |

#### Conversation 测试

| 用例 | 操作 | 期望 |
|------|------|------|
| 添加用户消息 | addUserMessage("hello") | 消息列表包含 1 条 UserMessage |
| 添加工具调用 | addAssistantToolCalls(...) | 消息列表包含 AssistantMessage with toolCalls |
| 添加工具结果 | addToolMessage(id, "result") | 消息列表包含 ToolMessage |
| 工具结果截断 | addToolMessage(id, 10000 字符内容) | ToolMessage 内容被截断到 4000 字符 |
| 消息窗口 | 添加 30 条消息 | getMessages() 返回最多 20 条 |
| System Message 保留 | 消息超过窗口后 | System Message 仍在首位 |
| clear() | 调用 clear() | 仅保留 System Message |

#### ContextBuildService 测试

| 用例 | 输入 | 期望 |
|------|------|------|
| 基础构建 | conversation + tools + workspace | ChatRequest.messages 首位是 SystemMessage |
| System Prompt 包含 workspace | workspace=/home/user/project | SystemMessage.content 包含路径 |
| 工具定义注入 | tools=[read_file, list_files] | ChatRequest.tools 包含 2 个 ToolDefinition |
| 历史消息顺序 | conversation 有 user + assistant 消息 | 消息顺序: System → User → Assistant |

#### ReActLoop 测试（使用 Mock）

| 用例 | Mock 配置 | 期望 |
|------|----------|------|
| Final Answer 直达 | chatModel 返回 content="answer", 无 toolCalls | success=true, finalAnswer="answer", steps 有 1 个 Final Answer step |
| 单工具调用 + Final | 第1轮返回 toolCalls=[read_file], 第2轮返回 content="done" | success=true, steps 有 2 个 step, 含 Action + Observation |
| 多工具调用 | 第1轮返回 toolCalls=[list_files, read_file] | 两个工具都被执行, Conversation 写入 2 个 ToolMessage |
| 工具执行失败 | toolRegistry 返回 ToolResult.error() | Observation 为错误信息, success=false, 但循环继续 |
| 未知工具 | toolCalls 包含未注册工具 | Observation 为 "Unknown tool", 循环继续 |
| 达到 maxTurns | maxTurns=3, LLM 每轮返回 toolCalls | 3 轮后停止, success=false, error 提示达到最大轮次 |
| LLM API 失败 | chatModel 抛出 ApiException | success=false, error 包含异常信息, steps 保留已执行记录 |
| LLM 空响应 | chatModel 返回空 choices | success=false, error 提示空响应 |

### 10.3 Mock 策略

**Mock ChatModelPort：**

```java
// 使用 Mockito 或手写 Stub
ChatModelPort mockChatModel = mock(ChatModelPort.class);
when(mockChatModel.chat(any()))
    .thenReturn(responseWithFinalAnswer("The answer is 42"))
    .thenReturn(responseWithToolCalls("read_file", Map.of("path", "pom.xml")));
```

**Mock ToolRegistry：**

```java
ToolRegistry mockToolRegistry = mock(ToolRegistry.class);
when(mockToolRegistry.definitions())
    .thenReturn(List.of(readFileTool.definition(), listFilesTool.definition()));
when(mockToolRegistry.execute(any(), any()))
    .thenReturn(ToolResult.success("Mock file content"));
```

**Mock AgentTask：** 使用真实的 `AgentTask` 实例（它是纯领域对象，不需要 mock），通过设置不同 maxTurns 值测试边界条件。

### 10.4 测试依赖

测试框架：JUnit 5 + Mockito（需添加 Maven test 依赖，或手写 Stub）

```
<dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>5.10.x</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.mockito</groupId>
    <artifactId>mockito-core</artifactId>
    <version>5.x</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.mockito</groupId>
    <artifactId>mockito-junit-jupiter</artifactId>
    <version>5.x</version>
    <scope>test</scope>
</dependency>
```

---

## 11. 实施计划

### 11.1 里程碑概览

| 里程碑 | 范围 | 交付物 | 依赖 | 预估工作量 |
|--------|------|--------|------|-----------|
| M1 | Agent 基础模型 | AgentRequest, AgentResponse, ReActStep, Conversation | 无 | 1-2 天 |
| M2 | 上下文构建 | ContextBuildService, System Prompt, 工具定义注入 | M1 | 1 天 |
| M3 | ReActLoop 核心 | LLM 调用编排, 响应解析, 最大轮次控制 | M1, M2 | 2 天 |
| M4 | Agent 集成 | DefaultAgent, AgentAppService 重构, Main.java 组装 | M3 | 1 天 |
| M5 | 测试与验收 | 单元测试, Mock 测试, 端到端验证 | M4 | 2 天 |

### 11.2 M1：Agent 基础模型

**交付物：**

| 文件 | 类型 | 说明 |
|------|------|------|
| `domain/agent/Agent.java` | 接口 | Agent 端口接口 |
| `domain/agent/AgentRequest.java` | 值对象 | Builder + 校验 |
| `domain/agent/AgentResponse.java` | 值对象 | 工厂方法 success() / error() |
| `domain/agent/ReActStep.java` | 值对象 | Builder 模式 |
| `domain/agent/Conversation.java` | 聚合 | 消息管理与截断 |

**验收标准：**
- 所有值对象可通过 Builder 正确构造
- AgentRequest 校验逻辑正确（空输入、无效路径等）
- Conversation 可正常添加各类型消息，窗口截断正确
- 通过单元测试

### 11.3 M2：上下文构建

**交付物：**

| 文件 | 类型 | 说明 |
|------|------|------|
| `domain/agent/ContextBuildService.java` | 领域服务 | System Prompt 构建 + ChatRequest 组装 |

**验收标准：**
- System Prompt 包含 Agent 身份、workspace 路径、当前日期
- ChatRequest.messages 首位为 SystemMessage
- ChatRequest.tools 包含从 ToolRegistry 获取的工具定义
- 通过单元测试

### 11.4 M3：ReActLoop 核心

**交付物：**

| 文件 | 类型 | 说明 |
|------|------|------|
| `domain/agent/ReActLoop.java` | 领域服务 | ReAct 循环逻辑 |

**验收标准：**
- Final Answer 场景：0 次工具调用，直接返回结果
- 单工具调用场景：1 次工具执行，Observation 写入 Conversation
- 多工具调用场景：按序执行多个工具
- 达到 maxTurns 后正确停止
- LLM 失败时不崩溃，返回错误信息 + 已执行 steps
- 通过 Mock 单元测试

### 11.5 M4：Agent 集成

**交付物：**

| 文件 | 类型 | 说明 |
|------|------|------|
| `domain/agent/DefaultAgent.java` | 实现 | Agent 端口默认实现 |
| `application/service/AgentAppService.java` | 变更 | 重构为委托 Agent 端口 |
| `application/dto/AgentResponseDTO.java` | 变更 | 新增 steps 字段，from() 工厂方法 |
| `Main.java` | 变更 | 组装 Agent 依赖链 |

**验收标准：**
- `DefaultAgent.run(request)` 可正常执行
- AgentAppService 正确委托 Agent 端口
- Main.java 依赖注入链完整，无编译错误
- CLI 可正常启动并执行简单任务

### 11.6 M5：测试与验收

**交付物：**

| 文件 | 类型 | 说明 |
|------|------|------|
| `test/.../agent/AgentRequestTest.java` | 单元测试 | AgentRequest 校验测试 |
| `test/.../agent/ConversationTest.java` | 单元测试 | Conversation 消息管理测试 |
| `test/.../agent/ContextBuildServiceTest.java` | 单元测试 | 上下文构建测试 |
| `test/.../agent/ReActLoopTest.java` | 组件测试 | ReAct 循环 Mock 测试 |
| `test/.../agent/DefaultAgentTest.java` | 组件测试 | Agent 端到端 Mock 测试 |

**验收标准（对应 PRD 第 11 章）：**

1. 可通过 `Agent.run` 提交用户任务
2. Agent 可构建包含 system prompt、用户消息、历史消息和工具定义的 ChatRequest
3. Agent 可调用 `ChatModelPort.chat()`
4. Agent 可识别 Final Answer 并结束任务
5. Agent 可识别 tool_calls 并调用 `ToolRegistry.execute()`
6. Agent 可把 ToolResult 转成 Observation
7. Agent 可把 Observation 追加回 Conversation
8. Agent 可执行多轮 ReAct 循环
9. Agent 达到 maxTurns 后会停止
10. AgentResponse 包含完整执行轨迹（ReActStep 列表）
11. 工具执行失败不会导致进程崩溃
12. 未知工具调用会生成失败 Observation
13. Shell 和写文件审批逻辑可通过工具层生效
14. 单元测试覆盖：Final Answer、单工具调用、多工具调用、工具失败、未知工具、达到最大轮次

---

## 12. 风险与应对

### 12.1 技术风险矩阵

| 风险 | 概率 | 影响 | 缓解措施 | 应急预案 |
|------|------|------|---------|---------|
| LLM 持续调用工具不结束（无限循环） | 中 | 高 | maxTurns 强制限制 + 每次循环检查 `canContinue()` | 达到 maxTurns 后终止，返回部分结果 |
| LLM 返回未知工具调用 | 低 | 中 | `ToolRegistryImpl.execute()` 返回错误，ReActLoop 将其作为失败 Observation | LLM 在下一轮看到错误后可调整策略 |
| 工具输出过长导致上下文膨胀 | 高 | 中 | Conversation 层截断 tool 消息（4000 字符） + 消息窗口限制（20 条） | 如果仍然超限，在 ContextBuildService 中进一步压缩历史消息 |
| 工具执行失败后 Agent 陷入死循环 | 中 | 中 | 失败 Observation 包含明确错误信息，LLM 可据此调整 | 如果 LLM 连续 3 轮调用同一失败工具，ReActLoop 可强制终止 |
| DeepSeek thinking 内容过长占据大量 token | 中 | 低 | thinking 内容不影响回合计数，openai 兼容 API 自动管理 | 通过 `reasoningEffort` 参数调整（"high" → "medium"） |
| CLI 交互与 ReAct 执行轨迹展示混乱 | 低 | 低 | AgentCli 在 AgentResponse 返回后统一展示轨迹 | 先实现基础展示，后续迭代优化格式 |
| 并发安全问题（如果未来支持多用户） | 低 | 中 | Conversation 和 SessionAppService 当前为单线程设计 | 如需要，Conversation 可加 synchronized 或使用 ConcurrentLinkedDeque |

### 12.2 已知未知项（需 Spike / 进一步调研）

1. **System Prompt 调优**：当前 System Prompt 为初始版本，实际 Agent 表现需要通过真实任务调优。建议在 M5 阶段收集 5-10 个典型任务的表现数据，迭代优化 Prompt。

2. **DeepSeek thinking 内容处理**：DR1 阶段 DeepSeek thinking 模式可能导致上下文膨胀。需要验证 `reasoningEffort="high"` 对 token 消耗和响应质量的实际影响。

3. **多轮对话中的上下文压缩**：当 Conversation 超过 20 条消息时，简单的 FIFO 截断可能丢弃关键上下文。后续可能需要实现更智能的摘要策略。

### 12.3 设计取舍记录

| 取舍 | 我们选择 | 而不是 | 因为 | 接受的代价 |
|------|---------|--------|------|-----------|
| LLM 端口 | 复用 ChatModelPort | 新建 CodingChatModel | 能力完全覆盖，减少重复抽象 | PRD 概念映射需要文档说明 |
| Conversation 存储 | 内存 ArrayList + Deque | Redis / MySQL | 保持架构简单，符合短期需求 | 进程重启后对话历史丢失 |
| 工具结果截断 | Conversation 层截断 | 各工具自行截断 | 集中管理，一致性更好 | Conversation 需要感知截断逻辑 |
| 错误处理 | 工具失败后继续循环 | 工具失败后终止 | LLM 可以基于错误调整策略 | 可能浪费轮次在不可恢复的错误上 |
| ReActStep | 不可变 + Builder | 可变对象 | 与现有领域对象风格一致，线程安全 | 每步新建对象有微小 GC 开销 |

---

## 附录 A：新增文件清单

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

src/test/java/com/flyagent/domain/agent/
  AgentRequestTest.java          # AgentRequest 单元测试
  ConversationTest.java          # Conversation 单元测试
  ContextBuildServiceTest.java   # ContextBuildService 单元测试
  ReActLoopTest.java             # ReActLoop Mock 组件测试
  DefaultAgentTest.java          # DefaultAgent Mock 组件测试
```

## 附录 B：变更文件清单

```
src/main/java/com/flyagent/
  application/service/AgentAppService.java     # 重构：注入 Agent 端口
  application/dto/AgentResponseDTO.java        # 扩展：新增 steps 字段
  interfaces/cli/AgentCli.java                 # 变更：展示 ReAct 执行轨迹
  Main.java                                    # 变更：组装 Agent 依赖链

pom.xml                                        # 变更：添加 JUnit 5 + Mockito 测试依赖
```

## 附录 C：与 PRD 的差异说明

| PRD 概念 | 技术方案实现 | 差异原因 |
|---------|-------------|---------|
| `CodingChatModel` 端口 | 复用 `ChatModelPort` | 现有端口已完全覆盖需求，新建端口违反 DRY |
| `ModelRequest` / `ModelResponse` / `ModelToolCall` | 复用 `ChatRequest` / `ChatResponse` / `ToolCall` | 现有模型已等价，不需要额外包装 |
| `domain/agent/` 包位置 | 保持一致 | 遵循 PRD 推荐结构 |
| `ReActLoop` 为 domain 服务 | 保持一致 | 遵循 PRD 推荐结构 |
| `Conversation.recentMessages(maxMessages)` | `Conversation.getMessages()` | 简化 API，窗口大小在构造时设定 |

---

文档结束。
