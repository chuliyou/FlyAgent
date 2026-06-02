# FlyAgent 一期技术方案：DeepSeek + DDD 架构 CLI Agent

版本：v0.3  
对应 PRD：`prd/一期PRD-Claude-Code风格CLI-Agent.md`  
项目名称：FlyAgent  
技术栈：Java 17 + Maven + DeepSeek API + MySQL 8.0.31 + Redis 8.8.0  
交付目标：实现一个基于 DDD 分层、接入 DeepSeek 模型、采用 ReAct 模式、不依赖第三方 Agent 框架的 CLI Coding Agent MVP。

## 1. 方案概述

FlyAgent 一期建设一个可在终端中运行的 Java CLI 编程 Agent。用户通过自然语言输入代码任务，Agent 调用 DeepSeek 模型理解需求，并采用 ReAct 模式在受控安全边界内使用本地工具完成目录查看、文件读取、文本搜索、文件写入、局部修改和命令执行。

本版本相较原技术方案做四项调整：

1. 模型调用从通用 OpenAI-compatible 适配调整为 DeepSeek 专用适配。
2. 系统架构从传统分层调整为 DDD 架构。
3. 数据层引入 MySQL 8.0.31 与 Redis 8.8.0，用于会话、任务、工具审计、配置与上下文缓存。
4. 一期 Agent 执行模式调整为 ReAct，即 Thought、Action、Observation、Final Answer 的有限循环。

一期核心闭环如下：

```text
用户输入
  -> CLI 接收命令
  -> 应用服务创建任务
  -> 领域层驱动 ReAct 执行循环
  -> Thought：DeepSeek 生成下一步思考摘要
  -> Action：DeepSeek 通过 tool calls 选择工具
  -> 必要时用户确认
  -> Observation：工具适配器执行并返回真实结果
  -> 将 Observation 写入会话上下文与审计记录
  -> 循环直到 Final Answer
```

一期重点不是实现完全自主编码，而是实现一个可控、安全、可解释、可扩展，并且具备清晰领域边界的最小可用 Agent 内核。

## 2. 建设目标

一期必须完成以下能力：

1. 支持交互式 CLI 会话。
2. 支持多轮自然语言对话。
3. 支持 DeepSeek Chat Completions API 调用。
4. 支持 DeepSeek tool calls 或 JSON 输出格式驱动工具调用。
5. 支持有限轮次 ReAct Agent Loop，默认最多 8 轮。
6. 支持本地工具调用，包括目录、文件、搜索、写入、补丁和 shell。
7. 支持 workspace 访问限制，禁止越权读写。
8. 支持 shell 命令执行前确认。
9. 支持写文件前展示变更摘要。
10. 支持 CLI 展示 Thought 摘要、Action、Observation 和 Final Answer。
11. 支持 MySQL 持久化会话、任务、消息、ReAct 步骤、工具调用和配置。
12. 支持 Redis 缓存短期上下文、ReAct 运行态、审批状态和任务运行态。
13. 支持配置文件、环境变量、命令行参数三类配置来源。
14. 支持 Maven 打包为可运行 jar。

## 3. 非建设范围

一期暂不实现以下能力：

1. 图形界面。
2. 多 Agent 协作。
3. 插件市场。
4. 自动 Git commit。
5. 长期向量记忆数据库。
6. 复杂任务规划器。
7. 远程开发环境。
8. IDE 插件。
9. 完整 MCP 协议兼容。
10. 自主无限循环执行。
11. 多模型供应商动态切换。
12. 复杂多分支任务规划器，ReAct 只作为一期的轻量执行循环。

## 4. DeepSeek 接入方案

### 4.1 接入范围

一期只实现 DeepSeek 官方 API 适配，不再将模型模块命名为 OpenAI-compatible 通用适配层。考虑到 DeepSeek API 请求格式兼容 OpenAI Chat Completions，底层 HTTP 请求仍可复用相似的消息结构，但工程命名、配置项、异常处理和模型参数以 DeepSeek 为准。

### 4.2 DeepSeek API 参数

| 项 | 取值 |
| --- | --- |
| API Base URL | `https://api.deepseek.com` |
| Chat Endpoint | `/chat/completions` |
| 默认模型 | `deepseek-v4-pro` |
| 快速模型 | `deepseek-v4-flash` |
| API Key 环境变量 | `DEEPSEEK_API_KEY` |
| 默认 stream | `false` |
| 默认 thinking | `enabled` |
| 默认 reasoning_effort | `high` |

说明：

1. 方案以 DeepSeek 官方文档为准。
2. DeepSeek 当前支持 `deepseek-v4-flash`、`deepseek-v4-pro` 等模型。
3. `deepseek-chat`、`deepseek-reasoner` 可作为兼容配置保留，但不作为一期默认值。
4. 一期默认非流式响应，后续可扩展 SSE 流式输出。

### 4.3 DeepSeek 请求示例

```http
POST https://api.deepseek.com/chat/completions
Authorization: Bearer ${DEEPSEEK_API_KEY}
Content-Type: application/json
```

```json
{
  "model": "deepseek-v4-pro",
  "messages": [
    {
      "role": "system",
      "content": "You are FlyAgent, a CLI coding agent."
    },
    {
      "role": "user",
      "content": "帮我分析这个项目结构"
    }
  ],
  "thinking": {
    "type": "enabled"
  },
  "reasoning_effort": "high",
  "stream": false
}
```

### 4.4 DeepSeek Tool Calls 策略

一期优先支持 DeepSeek tool calls。ReAct 中的 Action 通过 DeepSeek function tool calls 承载，工具定义按照 function tool 结构传递给模型：

```json
{
  "type": "function",
  "function": {
    "name": "read_file",
    "description": "Read a text file under current workspace.",
    "parameters": {
      "type": "object",
      "properties": {
        "path": {
          "type": "string"
        },
        "maxChars": {
          "type": "integer"
        }
      },
      "required": ["path"]
    }
  }
}
```

模型返回 `finish_reason=tool_calls` 时，Agent 将 `tool_calls` 记录为 ReAct Action，解析后执行对应工具。工具结果作为 Observation，以 `role=tool` 消息回传 DeepSeek，并同步写入 `ReActStep`。

### 4.5 JSON 输出兜底策略

如果 tool calls 调试阶段不稳定，一期允许通过 DeepSeek JSON Output 作为兜底方案：

最终回答：

```json
{
  "type": "final",
  "thought_summary": "已经获得足够信息，可以总结项目结构。",
  "content": "这是项目分析结果..."
}
```

工具调用：

```json
{
  "type": "tool_call",
  "thought_summary": "需要先读取 Maven 配置文件。",
  "tool": "read_file",
  "arguments": {
    "path": "pom.xml",
    "maxChars": 20000
  }
}
```

兜底解析策略：

1. 优先解析 DeepSeek 标准 tool calls。
2. 若不存在 tool calls，则解析 assistant content 中的 JSON。
3. 若 JSON 解析失败，将原始文本作为最终回答。
4. 若 JSON 类型未知，将错误写入会话并要求模型重新生成。

## 5. ReAct 执行模式

### 5.1 模式定义

一期 Agent 采用 ReAct 模式，即将一次任务拆解为有限轮次的：

```text
Thought -> Action -> Observation -> Final Answer
```

在 FlyAgent 中，各阶段含义如下：

| 阶段 | 含义 | 技术承载 |
| --- | --- | --- |
| Thought | Agent 对下一步动作的简短思考摘要 | assistant content 或内部 ReActStep |
| Action | Agent 选择一个或多个工具并生成参数 | DeepSeek tool calls |
| Observation | 工具真实执行结果 | role=tool 消息、ToolResult、ReActStep |
| Final Answer | 任务结束后的用户可读回答 | assistant content |

要求：

1. Thought 只展示简短思考摘要，不展示完整隐藏推理链。
2. Action 必须来自 DeepSeek tool calls 或 JSON 兜底工具调用。
3. Observation 必须来自真实工具执行结果，不允许模型伪造。
4. Final Answer 必须基于历史消息和 Observation 生成。
5. 每个任务最多执行 8 轮 ReAct，避免无限自主执行。

### 5.2 DeepSeek 与 ReAct 的关系

DeepSeek 负责生成 Thought 摘要、Action 和 Final Answer；FlyAgent 负责执行 Action、生成 Observation、维护安全边界和持久化审计。

推荐映射：

```text
DeepSeek assistant content       -> Thought 摘要或 Final Answer
DeepSeek message.tool_calls      -> Action
FlyAgent ToolResult              -> Observation
DeepSeek finish_reason=tool_calls -> 继续 ReAct
DeepSeek finish_reason=stop       -> 输出 Final Answer
```

当 DeepSeek 返回 `reasoning_content` 时，FlyAgent 只用于内部调试或审计开关，不默认展示完整内容。CLI 默认展示由系统提示词要求模型生成的简短 Thought 摘要。

### 5.3 ReAct Prompt 要求

系统提示词需要明确约束：

```text
You are FlyAgent, a CLI coding agent.
Use ReAct style for coding tasks:
- Thought: provide a brief next-step summary, not hidden chain-of-thought.
- Action: call one of the available tools when you need local information or changes.
- Observation: wait for tool results from the system. Never fabricate observations.
- Final Answer: answer the user when the task is complete.

Use tool calls for Action whenever a tool is needed.
Do not access files or run commands except through tools.
```

CLI 展示格式：

```text
Thought: 需要先查看项目结构和 Maven 配置。
Action: list_files {"path": ".", "maxDepth": 3}
Observation: 找到 pom.xml、src/main/java、src/test/java。
Action: read_file {"path": "pom.xml"}
Observation: 已读取 4,812 字符。
Final Answer: 这是一个 Java Maven 项目...
```

### 5.4 ReAct 终止条件

任务在以下任一场景结束：

1. DeepSeek 返回 Final Answer。
2. 达到最大 ReAct 轮次。
3. 用户拒绝必要审批。
4. 工具执行出现不可恢复错误。
5. DeepSeek 请求失败且重试后仍失败。

达到最大轮次时，CLI 应输出：

1. 已完成的 Action 列表。
2. 最后一条 Observation。
3. 当前未完成原因。
4. 建议用户继续输入更具体的下一步指令。

## 6. DDD 总体架构

### 6.1 分层架构

```text
+----------------------------------------------------------------+
| Interfaces Layer                                               |
| CLI Controller, Console Presenter, Command Parser               |
+-------------------------------+--------------------------------+
                                |
+-------------------------------v--------------------------------+
| Application Layer                                               |
| AgentAppService, SessionAppService, ToolExecutionAppService     |
| DTO, Command, Query, Transaction Boundary                       |
+-------------------------------+--------------------------------+
                                |
+-------------------------------v--------------------------------+
| Domain Layer                                                    |
| Aggregate, Entity, Value Object, Domain Service, Repository     |
| AgentSession, AgentTask, Conversation, ToolInvocation           |
+-------------------------------+--------------------------------+
                                |
+-------------------------------v--------------------------------+
| Infrastructure Layer                                            |
| DeepSeek Client, Tool Adapters, MySQL Repository, Redis Cache    |
| File System, Shell Executor, Config Loader, Safety Adapter       |
+----------------------------------------------------------------+
```

### 6.2 层职责

| 层 | 职责 | 依赖方向 |
| --- | --- | --- |
| Interfaces | 处理 CLI 输入输出、展示执行过程、解析内置命令 | 依赖 Application |
| Application | 编排用例、控制事务、调用领域对象和仓储接口 | 依赖 Domain |
| Domain | 表达 Agent 核心业务规则、状态变更和安全约束 | 不依赖外部框架 |
| Infrastructure | 实现 DeepSeek、MySQL、Redis、文件系统、Shell 等外部能力 | 依赖 Domain 接口 |

### 6.3 领域边界

一期划分 6 个核心领域概念：

| 领域概念 | 类型 | 说明 |
| --- | --- | --- |
| AgentSession | 聚合根 | 表示一次 CLI 会话，包含 workspace、上下文窗口和会话状态 |
| AgentTask | 聚合根 | 表示用户发起的一次任务，控制最大轮次、状态流转和最终结果 |
| Conversation | 实体 | 表示多轮消息上下文 |
| ReActStep | 实体/值对象 | 表示一轮 Thought、Action、Observation、Final Answer 记录 |
| ToolInvocation | 实体 | 表示一次工具调用，包括参数、结果、审批和执行状态 |
| WorkspacePolicy | 值对象/领域服务 | 表示 workspace 边界和文件安全规则 |

### 6.4 聚合关系

```text
AgentSession
  - SessionId
  - Workspace
  - Conversation
  - SessionStatus

AgentTask
  - TaskId
  - SessionId
  - UserInstruction
  - AgentTurnCount
  - TaskStatus
  - List<ReActStep>
  - List<ToolInvocation>

ReActStep
  - StepId
  - StepType
  - ThoughtSummary
  - Action
  - Observation
  - FinalAnswer

ToolInvocation
  - ToolInvocationId
  - ToolName
  - Arguments
  - ApprovalStatus
  - ExecutionStatus
  - ToolResult
```

设计约束：

1. `AgentSession` 管会话生命周期，不直接执行工具。
2. `AgentTask` 管一次用户任务的 ReAct Loop，不直接操作文件系统。
3. `ReActStep` 记录 Thought、Action、Observation 和 Final Answer 的执行轨迹。
4. `ToolInvocation` 记录工具调用意图、审批、执行状态和结果。
5. 文件和命令安全规则由领域服务先判断，再由基础设施适配器执行。

## 7. 推荐工程结构

```text
FlyAgent/
  pom.xml
  README.md
  prd/
    一期PRD-Claude-Code风格CLI-Agent.md
  技术方案/
    FlyAgent一期技术方案-Claude-Code风格CLI-Agent.md
  src/main/java/com/flyagent/
    Main.java
    interfaces/
      cli/
        AgentCli.java
        CliCommand.java
        CommandParser.java
        ConsolePresenter.java
        ConsoleIO.java
    application/
      command/
        StartSessionCommand.java
        SubmitUserTaskCommand.java
        ApproveToolCommand.java
      dto/
        AgentResponseDTO.java
        ToolExecutionDTO.java
        SessionDTO.java
      service/
        AgentAppService.java
        SessionAppService.java
        ToolExecutionAppService.java
    domain/
      model/
        session/
          AgentSession.java
          SessionId.java
          SessionStatus.java
          Workspace.java
        task/
          AgentTask.java
          TaskId.java
          TaskStatus.java
          AgentTurn.java
        react/
          ReActStep.java
          ReActStepId.java
          ReActStepType.java
          ThoughtSummary.java
          Observation.java
        conversation/
          Conversation.java
          Message.java
          MessageRole.java
        tool/
          ToolInvocation.java
          ToolInvocationId.java
          ToolName.java
          ToolArguments.java
          ToolResult.java
          ApprovalStatus.java
        model/
          ModelRequest.java
          ModelResponse.java
          ModelUsage.java
      service/
        ReActLoopDomainService.java
        ContextBuildDomainService.java
        ReActPromptDomainService.java
        WorkspacePolicyService.java
        CommandRiskDomainService.java
      repository/
        AgentSessionRepository.java
        AgentTaskRepository.java
        ConversationRepository.java
        ReActStepRepository.java
        ToolInvocationRepository.java
    infrastructure/
      config/
        AgentConfig.java
        ConfigLoader.java
        ConfigDefaults.java
      deepseek/
        DeepSeekChatModel.java
        DeepSeekChatRequest.java
        DeepSeekChatResponse.java
        DeepSeekToolCallParser.java
        DeepSeekException.java
      persistence/
        mysql/
          MysqlAgentSessionRepository.java
          MysqlAgentTaskRepository.java
          MysqlConversationRepository.java
          MysqlReActStepRepository.java
          MysqlToolInvocationRepository.java
          schema/
            V1__init.sql
        redis/
          RedisContextCache.java
          RedisReActRuntimeStore.java
          RedisApprovalStore.java
          RedisTaskRuntimeStore.java
      tools/
        Tool.java
        ToolDefinition.java
        ToolRegistry.java
        ListFilesTool.java
        ReadFileTool.java
        SearchTextTool.java
        WriteFileTool.java
        PatchFileTool.java
        RunShellTool.java
      safety/
        WorkspaceGuard.java
        CommandSafetyChecker.java
        ApprovalService.java
        FileChangePreviewer.java
      util/
        JsonUtils.java
        TextTruncator.java
        PathUtils.java
  src/test/java/com/flyagent/
```

## 8. 核心流程设计

### 8.1 CLI 启动流程

```text
Main.main
  -> ConfigLoader 加载配置
  -> 初始化 MySQL 数据源和 Redis 客户端
  -> 初始化 DeepSeekChatModel
  -> 初始化 ToolRegistry
  -> 初始化 Application Services
  -> AgentCli.start
  -> 创建 AgentSession
  -> 进入交互式循环
```

CLI 支持的内置命令：

| 命令 | 行为 |
| --- | --- |
| `/help` 或 `help` | 显示帮助信息 |
| `/clear` 或 `clear` | 清空当前会话上下文 |
| `/exit`、`exit`、`quit` | 退出 CLI |
| `/pwd` 或 `pwd` | 显示当前 workspace |
| `/session` | 显示当前会话 ID 和任务状态 |

### 8.2 Agent AppService 流程

```text
AgentCli 接收用户输入
  -> AgentAppService.submit(command)
  -> 加载 AgentSession
  -> 创建 AgentTask
  -> 调用 ReActLoopDomainService
  -> DeepSeekChatModel 生成 Thought + Action 或 Final Answer
  -> ToolExecutionAppService 执行 Action 对应的工具调用
  -> 保存 Conversation、AgentTask、ReActStep、ToolInvocation
  -> 返回 AgentResponseDTO
  -> ConsolePresenter 输出 ReAct 执行轨迹和最终结果
```

### 8.3 ReAct Agent Loop 流程

```text
for turn in 1..maxTurns:
  1. ContextBuildDomainService 构造系统提示词、历史消息、ReAct 步骤和工具定义
  2. DeepSeekChatModel.chat(request)
  3. 如果 DeepSeek 返回 Final Answer，保存 ReActStep 并结束
  4. 如果 DeepSeek 返回 tool calls，将其记录为 Action
  5. 创建 ReActStep(type=ACTION) 和 ToolInvocation
  6. 领域服务校验工具安全策略
  7. 需要审批时通过 ApprovalService 获取用户确认
  8. ToolExecutionAppService 执行工具
  9. ToolResult 作为 Observation 写入 Conversation、MySQL 和 Redis
  10. 继续下一轮 Thought

达到 maxTurns 后：
  将任务置为 HIT_MAX_TURNS，输出轮次上限提示、已完成 Action 和最后 Observation
```

## 9. 领域模型设计

### 9.1 AgentSession 聚合

职责：

1. 表示一次 CLI 会话。
2. 维护 workspace、会话状态、创建时间和最后活跃时间。
3. 关联 Conversation。
4. 支持清空上下文。

核心行为：

```java
public class AgentSession {
    public void activate();
    public void clearConversation();
    public void close();
    public boolean isActive();
}
```

### 9.2 AgentTask 聚合

职责：

1. 表示用户发起的一次自然语言任务。
2. 控制任务状态流转。
3. 控制 ReAct Loop 最大轮次。
4. 记录最终回答或失败原因。
5. 关联本次任务产生的 ReActStep 和 ToolInvocation。

状态流转：

```text
CREATED -> RUNNING -> WAITING_APPROVAL -> RUNNING -> COMPLETED
                         |                    |
                         v                    v
                      REJECTED              FAILED

RUNNING -> HIT_MAX_TURNS
```

核心行为：

```java
public class AgentTask {
    public void start();
    public void increaseTurn();
    public boolean canContinue();
    public void recordStep(ReActStep step);
    public void waitApproval(ToolInvocation invocation);
    public void complete(String finalAnswer);
    public void fail(String reason);
}
```

### 9.3 Conversation 实体

职责：

1. 保存用户消息、助手消息、工具消息。
2. 为 DeepSeek 请求构建消息窗口。
3. 对过长消息进行截断。
4. 保存 ReAct 步骤对应的消息投影。

消息类型：

| Role | 说明 |
| --- | --- |
| `system` | 系统提示词，不持久化或按版本持久化 |
| `user` | 用户输入 |
| `assistant` | DeepSeek 输出 |
| `tool` | 工具执行结果 |

### 9.4 ReActStep 实体

职责：

1. 表示一次 ReAct 步骤。
2. 记录 Thought 摘要、Action、Observation 或 Final Answer。
3. 关联 ToolInvocation，保证 Action 与工具调用审计可追踪。
4. 为 CLI 展示执行轨迹提供结构化数据。

步骤类型：

```text
THOUGHT
ACTION
OBSERVATION
FINAL_ANSWER
```

核心字段：

```java
public class ReActStep {
    private ReActStepId id;
    private TaskId taskId;
    private int turnIndex;
    private ReActStepType type;
    private String thoughtSummary;
    private ToolInvocationId toolInvocationId;
    private String actionName;
    private String actionArgumentsJson;
    private String observation;
    private String finalAnswer;
}
```

设计规则：

1. Thought 展示为简短摘要，不保存完整隐藏推理链。
2. Action 必须能关联到 ToolInvocation。
3. Observation 必须来自 ToolResult。
4. Final Answer 只能在任务完成或中止时写入。

### 9.5 ToolInvocation 实体

职责：

1. 表示一次工具调用。
2. 记录 DeepSeek 请求的工具名与参数。
3. 维护审批状态。
4. 维护执行状态和结果。

审批状态：

```text
NOT_REQUIRED
PENDING
APPROVED
REJECTED
```

执行状态：

```text
CREATED
RUNNING
SUCCEEDED
FAILED
SKIPPED
```

## 10. DeepSeek 模型模块设计

### 10.1 模型接口

领域层定义模型端口：

```java
public interface CodingChatModel {
    ModelResponse chat(ModelRequest request);
}
```

基础设施层实现 DeepSeek：

```java
public class DeepSeekChatModel implements CodingChatModel {
    private final HttpClient httpClient;
    private final AgentConfig config;

    @Override
    public ModelResponse chat(ModelRequest request) {
        // build DeepSeek request
        // call /chat/completions
        // parse assistant message or tool calls
    }
}
```

### 10.2 ModelRequest

```java
public class ModelRequest {
    private String model;
    private List<Message> messages;
    private List<ToolDefinition> tools;
    private boolean reactMode;
    private boolean stream;
    private ThinkingMode thinkingMode;
    private ReasoningEffort reasoningEffort;
}
```

### 10.3 ModelResponse

```java
public class ModelResponse {
    private String rawContent;
    private String thoughtSummary;
    private List<ToolInvocation> toolInvocations;
    private String finalAnswer;
    private String reasoningContent;
    private ModelUsage usage;
    private String finishReason;
}
```

映射说明：

1. `thoughtSummary` 来自 assistant content 中按 prompt 约束输出的简短摘要，或由应用层从结构化 JSON 中解析。
2. `toolInvocations` 来自 DeepSeek `message.tool_calls`，对应 ReAct 的 Action。
3. `reasoningContent` 只作为可选审计字段，不默认展示给用户。
4. `finalAnswer` 对应 DeepSeek 自然结束时的最终回答。

### 10.4 DeepSeek 异常处理

| 场景 | 处理方式 |
| --- | --- |
| API Key 缺失 | 启动时提示配置方式 |
| 401/403 | 提示 API Key 或权限异常 |
| 429 | 提示限流，并建议稍后重试 |
| 5xx | 提示 DeepSeek 服务异常 |
| timeout | 记录失败并保留任务状态 |
| malformed response | 保存原始响应并返回可读错误 |

## 11. 工具系统设计

### 11.1 Tool 接口

```java
public interface Tool {
    ToolName name();

    ToolDefinition definition();

    ToolResult execute(ToolExecutionContext context, ToolArguments arguments);
}
```

### 11.2 ToolExecutionContext

```java
public class ToolExecutionContext {
    private SessionId sessionId;
    private TaskId taskId;
    private Workspace workspace;
    private Duration timeout;
}
```

### 11.3 工具清单

| 工具 | 参数 | 说明 | 是否需要审批 |
| --- | --- | --- | --- |
| `list_files` | `path`, `maxDepth` | 列出 workspace 内目录结构 | 否 |
| `read_file` | `path`, `maxChars` | 读取文本文件内容 | 否 |
| `search_text` | `query`, `path`, `regex`, `maxResults` | 搜索文本内容 | 否 |
| `write_file` | `path`, `content` | 创建或覆盖文件 | 是 |
| `patch_file` | `path`, `oldText`, `newText` | 基于文本片段进行局部替换 | 是 |
| `run_shell` | `command`, `timeoutSeconds` | 执行 shell 命令 | 是 |

### 11.4 工具执行原则

1. 所有工具必须通过 `WorkspacePolicyService` 校验 workspace 边界。
2. 写操作和 shell 操作必须生成 `ToolInvocation` 审计记录。
3. 需要审批的工具必须先进入 `WAITING_APPROVAL` 状态。
4. 工具输出必须截断，避免上下文过长。
5. 工具执行结果必须回写 MySQL；短期上下文可写入 Redis。

## 12. 安全设计

### 12.1 Workspace 边界

所有文件路径在执行前统一经过规范化：

```java
Path resolved = workspace.resolve(inputPath).normalize();
if (!resolved.startsWith(workspace)) {
    throw new SecurityException("Path is outside workspace");
}
```

安全规则：

1. 禁止绝对路径越过 workspace。
2. 禁止 `../` 逃逸。
3. 禁止符号链接逃逸，必要时使用 `toRealPath()` 二次校验。
4. 工具返回中优先展示相对路径。

### 12.2 命令安全检查

`CommandRiskDomainService` 负责领域侧风险判断，`CommandSafetyChecker` 负责基础设施侧命令特征匹配。

默认拒绝命令：

| 类型 | 示例 |
| --- | --- |
| 删除根目录或递归强删 | `rm -rf /`、`del /s` |
| 强制重置 | `git reset --hard` |
| 格式化磁盘 | `format` |
| 关机重启 | `shutdown`、`reboot` |
| 权限破坏 | `chmod -R 777 /` |

一期策略：

1. 命中高危规则直接拒绝。
2. 普通命令需要用户确认。
3. 二义性高的命令显示额外警告。
4. 所有 shell 命令都以 workspace 作为工作目录执行。

### 12.3 用户确认

确认交互示例：

```text
Agent wants to run:
mvn test

Allow? [y/N]
```

写文件确认示例：

```text
Agent wants to modify:
src/main/java/com/demo/UserService.java

Change summary:
- replace 1 matched block
- old size: 1820 chars
- new size: 1946 chars

Allow? [y/N]
```

审批结果写入：

1. MySQL：记录审批动作、工具调用、执行结果。
2. Redis：保存当前等待审批的任务运行态，避免 CLI 输出与应用服务状态不一致。

## 13. 数据库设计

### 13.1 MySQL 8.0.31 使用范围

MySQL 用于持久化核心业务数据：

1. 会话信息。
2. 用户任务。
3. 对话消息。
4. 工具调用记录。
5. 工具审批记录。
6. 模型调用用量。
7. 配置快照。

### 13.2 Redis 8.8.0 使用范围

Redis 用于短期运行态与缓存：

1. 当前会话上下文窗口缓存。
2. 等待审批的工具调用状态。
3. AgentTask 运行态。
4. DeepSeek 请求去重键。
5. 简单限流计数。
6. CLI 会话心跳。

Redis 不作为最终审计数据源，关键记录仍以 MySQL 为准。

### 13.3 MySQL 表设计

#### agent_session

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | varchar(64) | 会话 ID |
| workspace | varchar(1024) | 工作目录 |
| status | varchar(32) | 会话状态 |
| created_at | datetime(3) | 创建时间 |
| updated_at | datetime(3) | 更新时间 |
| closed_at | datetime(3) | 关闭时间 |

#### agent_task

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | varchar(64) | 任务 ID |
| session_id | varchar(64) | 会话 ID |
| user_instruction | text | 用户任务 |
| status | varchar(32) | 任务状态 |
| max_turns | int | 最大轮次 |
| current_turn | int | 当前轮次 |
| final_answer | mediumtext | 最终回答 |
| fail_reason | text | 失败原因 |
| created_at | datetime(3) | 创建时间 |
| updated_at | datetime(3) | 更新时间 |

#### conversation_message

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint unsigned auto_increment | 主键 |
| session_id | varchar(64) | 会话 ID |
| task_id | varchar(64) | 任务 ID |
| role | varchar(32) | `user`、`assistant`、`tool` |
| content | mediumtext | 消息内容 |
| token_count | int | 估算 token 数 |
| created_at | datetime(3) | 创建时间 |

#### tool_invocation

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | varchar(64) | 工具调用 ID |
| task_id | varchar(64) | 任务 ID |
| tool_name | varchar(128) | 工具名 |
| arguments_json | json | 工具参数 |
| approval_status | varchar(32) | 审批状态 |
| execution_status | varchar(32) | 执行状态 |
| result_text | mediumtext | 工具结果 |
| error_text | text | 错误信息 |
| created_at | datetime(3) | 创建时间 |
| updated_at | datetime(3) | 更新时间 |

#### model_call_log

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint unsigned auto_increment | 主键 |
| task_id | varchar(64) | 任务 ID |
| provider | varchar(32) | 固定为 `deepseek` |
| model | varchar(128) | 模型名 |
| request_hash | varchar(128) | 请求摘要 |
| finish_reason | varchar(64) | 结束原因 |
| prompt_tokens | int | 输入 token |
| completion_tokens | int | 输出 token |
| total_tokens | int | 总 token |
| latency_ms | int | 请求耗时 |
| created_at | datetime(3) | 创建时间 |

#### agent_config_snapshot

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint unsigned auto_increment | 主键 |
| session_id | varchar(64) | 会话 ID |
| config_json | json | 配置快照，不保存明文 API Key |
| created_at | datetime(3) | 创建时间 |

### 13.4 Redis Key 设计

| Key | 类型 | TTL | 说明 |
| --- | --- | --- | --- |
| `flyagent:session:{sessionId}:context` | string/list | 24h | 会话上下文窗口 |
| `flyagent:task:{taskId}:runtime` | hash | 24h | 任务运行态 |
| `flyagent:approval:{invocationId}` | string | 30m | 等待审批状态 |
| `flyagent:model:req:{hash}` | string | 5m | DeepSeek 请求去重 |
| `flyagent:rate:{apiKeyHash}` | counter | 1m | 简单限流计数 |

## 14. 上下文管理

### 14.1 上下文来源

1. 当前用户输入。
2. MySQL 中的最近对话消息。
3. Redis 中的短期上下文窗口。
4. 当前 workspace 摘要。
5. 工具定义。
6. 上一轮工具执行结果。

### 14.2 截断策略

| 内容 | 默认限制 |
| --- | --- |
| 最近对话消息 | 20 条 |
| 文件读取结果 | 20,000 字符 |
| shell 输出 | 30,000 字符 |
| 搜索结果 | 100 条 |
| Agent 最大循环轮次 | 8 轮 |

工具结果过长时使用 `TextTruncator` 截断：

```text
[Output truncated: showing first 30000 chars]
```

## 15. 配置设计

### 15.1 配置文件路径

默认配置文件：

```text
.agent/config.properties
```

### 15.2 配置项

```properties
deepseek.baseUrl=https://api.deepseek.com
deepseek.apiKey=
deepseek.model=deepseek-v4-pro
deepseek.stream=false
deepseek.thinking=enabled
deepseek.reasoningEffort=high

mysql.url=jdbc:mysql://localhost:3306/flyagent?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
mysql.username=root	
mysql.password=1234

redis.host=localhost
redis.port=6379
redis.password=123456
redis.database=0

workspace=.
maxTurns=8
requireShellApproval=true
requireWriteApproval=true
shellTimeoutSeconds=120
maxFileChars=20000
maxShellOutputChars=30000
```

### 15.3 环境变量

| 环境变量 | 对应配置 |
| --- | --- |
| `DEEPSEEK_API_KEY` | `deepseek.apiKey` |
| `FLYAGENT_DEEPSEEK_BASE_URL` | `deepseek.baseUrl` |
| `FLYAGENT_DEEPSEEK_MODEL` | `deepseek.model` |
| `FLYAGENT_MYSQL_URL` | `mysql.url` |
| `FLYAGENT_MYSQL_USERNAME` | `mysql.username` |
| `FLYAGENT_MYSQL_PASSWORD` | `mysql.password` |
| `FLYAGENT_REDIS_HOST` | `redis.host` |
| `FLYAGENT_REDIS_PORT` | `redis.port` |
| `FLYAGENT_WORKSPACE` | `workspace` |

### 15.4 优先级

```text
命令行参数 > 环境变量 > 配置文件 > 默认值
```

命令行参数示例：

```bash
java -jar flyagent.jar --workspace . --deepseek-model deepseek-v4-pro
```

## 16. CLI 交互设计

启动后显示：

```text
FlyAgent started.
Provider: DeepSeek
Model: deepseek-v4-pro
Workspace: J:\code\demo
Session: ses_20260602_001
Type /help for commands.
>
```

任务执行期间输出三类信息：

```text
Thinking: 正在分析项目结构...
Action: read_file pom.xml
Result: 已读取 4,812 chars
```

最终输出：

```text
Final:
该项目是一个 Spring Boot Maven 项目，核心模块包括...
```

异常输出：

```text
Error:
DeepSeek request failed: 429 rate limited. Please retry later.
```

## 17. Maven 与依赖建议

一期允许使用通用工程库，但不引入 LangChain、AutoGPT、Semantic Kernel 等第三方 Agent 框架。

推荐依赖：

| 依赖 | 用途 |
| --- | --- |
| Jackson Databind | JSON 序列化与反序列化 |
| MySQL Connector/J | 连接 MySQL 8.0.31 |
| HikariCP | 数据库连接池 |
| Jedis 或 Lettuce | 连接 Redis 8.8.0 |
| Flyway | MySQL schema 版本管理 |
| JUnit 5 | 单元测试 |
| Maven Shade Plugin | 生成可运行 fat jar |
| SLF4J + Logback | 日志 |

`pom.xml` 关键配置建议：

```xml
<properties>
    <maven.compiler.source>17</maven.compiler.source>
    <maven.compiler.target>17</maven.compiler.target>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
</properties>
```

## 18. 测试方案

### 18.1 单元测试

| 测试对象 | 测试点 |
| --- | --- |
| AgentTask | 状态流转、最大轮次控制 |
| WorkspacePolicyService | 正常路径、`../` 逃逸、绝对路径、符号链接 |
| CommandRiskDomainService | 高危命令识别、普通命令放行 |
| DeepSeekToolCallParser | 标准 tool calls、JSON 兜底、异常响应 |
| TextTruncator | 长文本截断、短文本保持 |
| ToolRegistry | 注册、查找、重复名称处理 |
| PatchFileTool | 单次命中、多次命中、未命中 |

### 18.2 集成测试

1. 使用临时 workspace 创建 Maven 示例项目。
2. 使用测试 MySQL schema 验证会话、任务、消息、工具调用落库。
3. 使用测试 Redis 验证上下文缓存、审批状态 TTL。
4. Mock DeepSeek HTTP 响应验证 Agent Loop。
5. 调用 `list_files` 验证目录输出。
6. 调用 `read_file` 验证文本读取。
7. 调用 `search_text` 验证命中行号。
8. 调用 `write_file` 验证文件创建。
9. 调用 `patch_file` 验证局部修改。
10. 模拟 `run_shell` 执行安全命令。

### 18.3 手动验收用例

| 用例 | 输入 | 期望 |
| --- | --- | --- |
| 项目分析 | `帮我分析这个项目结构` | Agent 读取目录和关键文件后输出总结 |
| 搜索类 | `帮我找到 UserService` | Agent 返回文件路径和相关上下文 |
| 修改文件 | `给 createUser 增加邮箱校验` | Agent 修改文件前要求确认，确认后写入 |
| 运行测试 | `帮我运行测试` | 展示 `mvn test` 并等待确认 |
| 越权访问 | `读取 ../secret.txt` | 工具拒绝并提示越过 workspace |
| 危险命令 | `执行 git reset --hard` | 命令被拒绝或二次确认 |
| 会话持久化 | 退出后重新进入同一 session | 可查询历史消息和任务记录 |
| 审批缓存 | 写文件等待确认 | Redis 中存在审批状态并设置 TTL |

## 19. 打包与运行

### 19.1 本地开发

```bash
mvn test
mvn package
```

### 19.2 初始化数据库

```sql
CREATE DATABASE flyagent DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER 'flyagent'@'%' IDENTIFIED BY 'your-password';
GRANT ALL PRIVILEGES ON flyagent.* TO 'flyagent'@'%';
FLUSH PRIVILEGES;
```

### 19.3 运行

```bash
java -jar target/flyagent.jar --workspace . --deepseek-model deepseek-v4-pro
```

### 19.4 配置 DeepSeek API Key

方式一：环境变量

```bash
set DEEPSEEK_API_KEY=your-api-key
```

方式二：配置文件

```text
.agent/config.properties
```

```properties
deepseek.apiKey=your-api-key
deepseek.model=deepseek-v4-pro
```

## 20. 开发里程碑

### M1：工程初始化与 DDD 骨架

交付内容：

1. Maven 项目骨架。
2. `interfaces`、`application`、`domain`、`infrastructure` 分层。
3. 领域模型基础类。
4. CLI 交互循环。
5. `/help`、`/clear`、`/exit`、`/pwd`、`/session` 内置命令。

验收标准：

1. `mvn test` 可执行。
2. `java -jar target/flyagent.jar` 可进入 CLI。
3. DDD 分层依赖方向清晰，Domain 不依赖 Infrastructure。

### M2：配置、MySQL 与 Redis

交付内容：

1. `AgentConfig`。
2. `ConfigLoader`。
3. MySQL 数据源。
4. Redis 客户端。
5. Flyway 初始化脚本。
6. Repository 接口与 MySQL 实现。

验收标准：

1. 支持配置文件、环境变量、命令行参数。
2. 会话和任务可落库。
3. Redis 可写入上下文缓存和审批状态。

### M3：DeepSeek 模型调用

交付内容：

1. `CodingChatModel` 领域端口。
2. `DeepSeekChatModel` 基础设施实现。
3. DeepSeek tool calls 解析。
4. JSON 输出兜底解析。
5. 普通问答能力。

验收标准：

1. 可以调用 DeepSeek 并返回回答。
2. API Key 缺失时提示清晰。
3. 模型请求和用量写入 `model_call_log`。

### M4：工具系统基础

交付内容：

1. `Tool` 接口。
2. `ToolRegistry`。
3. `WorkspacePolicyService`。
4. `list_files`、`read_file`、`search_text`。

验收标准：

1. 工具只能访问 workspace 内文件。
2. Agent 可读取项目结构并总结。
3. 搜索结果包含路径和行号。

### M5：Agent Loop 与会话上下文

交付内容：

1. `AgentLoopDomainService`。
2. `ContextBuildDomainService`。
3. Conversation 持久化。
4. Redis 上下文窗口缓存。
5. 最大轮次控制。

验收标准：

1. Agent 能连续调用多个工具完成任务。
2. 达到最大轮次后能正常停止。
3. 工具失败时不会导致 CLI 崩溃。

### M6：写文件、命令执行与审批

交付内容：

1. `write_file`。
2. `patch_file`。
3. `run_shell`。
4. `ApprovalService`。
5. `CommandRiskDomainService`。
6. `FileChangePreviewer`。

验收标准：

1. 写文件前展示变更摘要并要求确认。
2. shell 执行前展示命令并要求确认。
3. 危险命令被拒绝或二次确认。
4. 工具调用、审批和结果完整落库。

### M7：测试、打包与文档

交付内容：

1. 核心单元测试。
2. MySQL/Redis 集成测试说明。
3. 手动验收脚本或说明。
4. Maven 打包配置。
5. README。

验收标准：

1. `mvn clean package` 通过。
2. 生成可运行 jar。
3. 完成 PRD 中一期验收标准。

## 21. 风险与应对

| 风险 | 影响 | 应对 |
| --- | --- | --- |
| DeepSeek tool calls 解析不稳定 | 工具调用失败 | 支持 JSON Output 兜底 |
| DeepSeek 模型名变化 | 调用失败 | 模型名配置化，并在文档中提示以官方文档为准 |
| patch 精度不足 | 误改文件 | 一期只允许 oldText 单次精确命中 |
| shell 命令安全边界不足 | 误执行危险操作 | 默认确认，高危规则直接拒绝，所有命令限定 workspace |
| 上下文过长 | 请求失败或成本升高 | 最近消息保留、工具结果截断、Redis 缓存窗口 |
| MySQL/Redis 不可用 | CLI 无法启动或无法持久化 | 启动时健康检查，错误信息明确；开发环境可配置降级策略 |
| API 网络不稳定 | 用户体验差 | 输出可读错误，保留任务状态，允许用户重试 |

## 22. 一期验收清单

1. 可以通过 `mvn clean package` 打包。
2. 可以通过 `java -jar target/flyagent.jar` 启动。
3. 可以进入交互式 CLI。
4. 可以使用 `/help`、`/clear`、`/exit`、`/pwd`、`/session`。
5. 可以调用 DeepSeek 完成普通问答。
6. 可以解析 DeepSeek tool calls 或 JSON 工具调用。
7. 可以读取当前项目文件并总结项目结构。
8. 可以搜索代码内容。
9. 可以在确认后执行 `mvn test`。
10. 可以在确认后创建或修改文件。
11. 不能访问 workspace 外文件。
12. 危险命令不会被自动执行。
13. 会话、任务、消息、工具调用、模型调用记录可写入 MySQL。
14. 当前上下文和审批状态可写入 Redis。
15. 工具失败、模型失败、配置缺失时有可读错误信息。

## 23. 后续演进方向

二期可重点增强以下方向：

1. DeepSeek 流式输出。
2. DeepSeek thinking 内容展示策略。
3. 更强 patch 算法和 diff 展示。
4. Git diff、回滚和提交消息生成。
5. 多模型供应商切换。
6. Token 预算管理。
7. 项目索引与代码语义检索。
8. 非交互模式。
9. 插件式工具系统。
10. MCP 协议兼容。
11. 长任务规划器。
12. MySQL 任务历史检索与审计报表。

## 24. 参考资料

1. DeepSeek API Docs：`https://api-docs.deepseek.com/`
2. DeepSeek Chat Completions API：`https://api-docs.deepseek.com/api/create-chat-completion`
3. DeepSeek curl 示例：`https://api-docs.deepseek.com/api_samples/chat_curl/`

## 25. 总结

FlyAgent 一期技术方案调整为 DeepSeek 专用模型接入，并采用 DDD 分层组织核心业务逻辑。Domain 层负责表达 Agent 会话、任务、上下文、工具调用和安全策略；Application 层负责编排用例；Infrastructure 层负责 DeepSeek、MySQL、Redis、文件系统和 shell 适配；Interfaces 层负责 CLI 交互。

一期实现后，FlyAgent 应能在真实代码仓库中完成基础阅读、搜索、修改、命令执行和多轮反馈，并通过 MySQL 与 Redis 保留关键业务记录和运行态，为后续扩展流式输出、代码索引、插件系统和复杂任务规划提供稳定内核。
