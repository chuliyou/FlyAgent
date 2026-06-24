# Proposal: 新增 Agent 与 ReAct 循环

## 概述

为 FlyAgent 实现 Agent 编排层，通过 ReAct（Reasoning + Acting）有限循环将 LLM 调用能力（ChatModelPort）与工具执行能力（ToolRegistry + 5 个基础工具）串联起来，形成完整的智能体执行核心。完成后，用户可以提交自然语言任务，Agent 在有限轮次内自主选择工具、读取或操作本地环境，并基于工具结果输出最终回答。

## 背景与动机

### 当前状态

第一次开发已完成 DeepSeek API 客户端（`ChatModelPort` + `DeepSeekChatModel`），第二次开发已完成 5 个基础工具（`read_file`、`write_file`、`list_files`、`run_shell`、`create_project_structure`）及完整的工具层基础设施（`Tool`、`ToolRegistry`、`ToolResult`、`ToolExecutionContext`、`WorkspaceGuard`、`ApprovalHandler` 等）。

当前项目已具备的能力：

- DDD + 六边形分层架构（Interfaces → Application → Domain ← Infrastructure）
- DeepSeek Chat Completions API 完整调用能力（支持 thinking 模式、tool_calls）
- 多态消息体系（System/User/Assistant/Tool Message）
- 5 个基础本地工具及线程安全的 `ToolRegistryImpl`
- 安全校验（`WorkspaceGuard` 路径越权防护、`CommandSafetyChecker` 危险命令检测）
- 审批机制（`ApprovalHandler` 接口 + `ConsoleApprovalHandler`）
- 会话管理（`AgentSession` + `SessionAppService`）
- 任务状态机（`AgentTask`，支持 CREATED→RUNNING→COMPLETED/FAILED/HIT_MAX_TURNS）

**核心痛点：** 模型的"大脑"和工具的"手脚"各自独立，缺少将它们串联起来的 Agent 执行核心。当前 `AgentAppService` 仅做单轮对话，不支持工具调用循环和多轮上下文管理。

### 为什么现在做

1. **最小可用闭环：** 第三次开发完成后，FlyAgent 首次具备"理解任务 → 选择工具 → 执行工具 → 观察结果 → 输出回答"的完整 Agent 能力，达成一期 PRD 的核心目标
2. **依赖已就绪：** LLM 调用和工具执行均已独立开发并验证，Agent 层只需编排协调，不引入新的外部能力依赖
3. **架构自然演进：** 新增的 Agent 领域包（`domain/agent/`）完全遵循现有 DDD 分层，通过端口/适配器模式与已有组件对接，架构一致性高

## 交付成果

### 1. Agent 领域模型（8 个新增文件）

| 文件 | 类型 | 说明 |
|------|------|------|
| `Agent.java` | 领域端口接口 | Agent 统一入口，`run(AgentRequest) → AgentResponse` |
| `DefaultAgent.java` | 默认实现 | 组装 Conversation、AgentTask、ReActLoop，委托执行 |
| `AgentRequest.java` | 值对象 | 封装 userInput、workspace、maxTurns，Builder + 校验 |
| `AgentResponse.java` | 值对象 | 封装 finalAnswer、error、steps、totalTurns |
| `ReActLoop.java` | 领域服务 | ReAct 循环核心逻辑（调用 LLM → 解析响应 → 执行工具 → 记录步骤） |
| `ReActStep.java` | 值对象 | 单步执行记录（turn、thought、action、observation、finalAnswer） |
| `Conversation.java` | 领域聚合 | 对话历史管理（System Prompt + 消息窗口 + 工具结果截断） |
| `ContextBuildService.java` | 领域服务 | 组装 ChatRequest（System Prompt + 工具定义 + 历史消息） |

### 2. 现有文件变更（4 个文件）

| 文件 | 变更类型 | 说明 |
|------|---------|------|
| `AgentAppService.java` | 重构 | 从直接调用 `ChatModelPort` 改为委托 `Agent` 端口 |
| `AgentResponseDTO.java` | 扩展 | 新增 `steps`、`totalTurns` 字段 |
| `AgentCli.java` | 变更 | 新增 ReAct 执行轨迹展示逻辑 |
| `Main.java` | 变更 | 新增 Agent + ReActLoop 依赖组装 |

### 3. 测试（5 个测试类）

- `AgentRequestTest` — 输入校验测试
- `ConversationTest` — 消息管理与截断测试
- `ContextBuildServiceTest` — 上下文构建测试
- `ReActLoopTest` — ReAct 循环 Mock 组件测试
- `DefaultAgentTest` — Agent 端到端 Mock 测试

### 非目标

- 完整 CLI 交互体验优化（本期仅基础展示）
- MySQL 持久化 / Redis 缓存
- 多 Agent 协作
- 流式输出（streaming）
- 自动 Git commit
- 复杂工具审批 UI
- 插件化工具加载

## 技术方案

### 架构原则

- **复用现有端口，不新建冗余抽象：** `ChatModelPort` 已覆盖 tool_calls、messages、thinking 等全部能力，不再新建 `CodingChatModel` 端口
- **Agent 层只依赖端口接口：** `ReActLoop` 通过 `ChatModelPort` 和 `ToolRegistry` 接口编程，不直接依赖 `DeepSeekChatModel` 或具体工具类
- **领域对象不可变：** `AgentRequest`、`AgentResponse`、`ReActStep` 均采用 Builder 模式 + final 字段
- **同步调用：** 与现有 CLI 请求-响应模式保持一致，不引入异步框架

### ReAct 循环流程

```
用户任务 → ContextBuildService 构建上下文 → ChatModelPort.chat()
  → 解析响应：
    ├── Final Answer (content 非空 + 无 tool_calls) → 结束
    ├── Tool Calls → ToolRegistry.execute() → Observation 写入 Conversation → 下一轮
    └── 异常/空响应 → 返回失败
  → 循环直到 Final Answer 或达到 maxTurns（默认 8）
```

### 关键设计决策

| 决策 | 选择 | 理由 |
|------|------|------|
| LLM 端口 | 复用 `ChatModelPort` | 能力完全覆盖，避免重复抽象 |
| Conversation 存储 | 内存 ArrayList + Deque | 短期无持久化需求，保持架构简洁 |
| 工具结果截断 | Conversation 层统一截断（4000 字符） | 集中管理，一致性更好 |
| 工具失败处理 | 失败 Observation 回传，循环继续 | LLM 可基于错误调整策略 |

## 依赖关系

- **依赖已完成：** M1（DDD 骨架）、M3（DeepSeek 客户端）、第二次开发（工具层基础设施 + 5 个工具）
- **依赖的现有端口：** `ChatModelPort`、`ToolRegistry`、`ToolResult.toToolMessage()`
- **依赖的现有领域模型：** `AgentTask`、`AgentSession`、`Message` 多态体系、`ToolDefinition`
- **不依赖：** 任何第三方 Agent 框架（LangChain、AutoGPT、Semantic Kernel 等）

## 风险与应对

| 风险 | 概率 | 影响 | 缓解措施 |
|------|------|------|---------|
| LLM 持续调用工具不结束 | 中 | 高 | `maxTurns=8` 硬限制 + `AgentTask.canContinue()` 每轮检查 |
| 工具输出过长致上下文膨胀 | 高 | 中 | Conversation 层截断（4000 字符/条）+ 20 条消息窗口 |
| 工具失败后 Agent 死循环 | 中 | 中 | 失败 Observation 含明确错误信息，LLM 可据此调整策略 |
| Agent 层耦合具体实现 | 低 | 中 | 只依赖 `ChatModelPort` 和 `ToolRegistry` 端口接口 |
| DeepSeek thinking 内容消耗大量 token | 中 | 低 | thinking 内容不影响轮次计数，可调整 `reasoningEffort` 参数 |

## 参考资料

- [PRD: 第三次开发 PRD -- Agent 与 ReAct 循环](../../prd/第三次开发PRD-Agent与ReAct循环.md)
- [技术方案: 第三次开发 -- Agent 与 ReAct 循环 技术方案](../../../技术方案/第三次开发-Agent与ReAct循环-技术方案.md)
- [系统架构: project.md](../../../project.md)
