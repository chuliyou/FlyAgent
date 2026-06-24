# Tasks: 新增 Agent 与 ReAct 循环

## M1: Agent 基础模型

- [x] 1.1 创建 `Agent` 端口接口 at `domain/agent/Agent.java`
  - 定义 `AgentResponse run(AgentRequest request)` 方法
  - 遵循项目已有端口接口风格（Javadoc + 简洁签名）
- [x] 1.2 创建 `AgentRequest` 值对象 at `domain/agent/AgentRequest.java`
  - 字段：`sessionId`, `userInput`, `workspace`, `maxTurns`
  - Builder 模式 + `build()` 校验（userInput 非空、workspace 存在且为目录、maxTurns clamp [1,100]）
  - 所有字段 final，不可变
- [x] 1.3 创建 `AgentResponse` 值对象 at `domain/agent/AgentResponse.java`
  - 字段：`success`, `finalAnswer`, `error`, `steps`, `totalTurns`
  - 工厂方法：`success(finalAnswer, steps, totalTurns)` / `error(error, steps, totalTurns)`
  - 所有字段 final，不可变
- [x] 1.4 创建 `ReActStep` 值对象 at `domain/agent/ReActStep.java`
  - 字段：`turn`, `thought`, `actionName`, `actionArguments`, `observation`, `finalAnswer`, `success`, `error`
  - Builder 模式，支持三种形态（Tool Call / Final Answer / Error）
  - `observation` 字段存储截断后的结果
- [x] 1.5 创建 `Conversation` 领域聚合 at `domain/agent/Conversation.java`
  - 内部数据结构：`SystemMessage` + `Deque<Message>`（非 System 消息队列）
  - 方法：`addUserMessage()`, `addAssistantMessage()`, `addAssistantToolCalls()`, `addToolMessage()`, `getMessages()`, `clear()`, `size()`
  - 工具结果截断：`maxToolResultChars`（默认 4000），使用 `TextTruncator.truncate()`
  - 消息窗口限制：`getMessages()` 返回 System Message + 最近 `(maxMessages - 1)` 条
- [x] 1.6 编写 M1 单元测试
  - `AgentRequestTest`: 正常构造、空 userInput、null workspace、workspace 不存在、maxTurns 默认值、maxTurns 超出范围
  - `ConversationTest`: 添加用户消息、添加工具调用、添加工具结果、结果截断、消息窗口、System Message 保留、clear()

## M2: 上下文构建

- [x] 2.1 创建 `ContextBuildService` 领域服务 at `domain/agent/ContextBuildService.java`
  - 方法：`build(Conversation conversation, List<ToolDefinition> toolDefinitions, Path workspacePath) → ChatRequest`
  - System Prompt 生成：包含 Agent 身份、ReAct 指令、workspace 路径（绝对路径）、当前日期
  - 消息列表组装：SystemMessage 在首位 + 历史消息
  - 工具定义注入：通过 `toolDefinitions` 参数传入
  - `ChatRequest` 组装：messages + tools + thinking=true + stream=false
- [x] 2.2 编写 M2 单元测试
  - `ContextBuildServiceTest`: 基础构建、System Prompt 含 workspace、工具定义注入、历史消息顺序

## M3: ReActLoop 核心

- [x] 3.1 创建 `ReActLoop` 领域服务 at `domain/agent/ReActLoop.java`
  - 构造器注入：`ChatModelPort` + `ToolRegistry`
  - 方法：`execute(AgentRequest, ToolExecutionContext, ContextBuildService, Conversation, AgentTask) → AgentResponse`
  - 实现内容：
    - 循环控制：`while (task.canContinue())` — RUNNING && currentTurn < maxTurns
    - 上下文构建：每轮调用 `contextBuilder.build()`
    - LLM 调用：`chatModel.chat(chatRequest)`，捕获 `ApiException`
    - 响应解析：通过 `ChatChoice.getFinishReason()` + `AssistantMessage.hasToolCalls()` 判断
    - 情况 A (Final Answer)：记录 ReActStep(finalAnswer)，`task.complete()`，返回成功
    - 情况 B (Tool Calls)：逐个执行工具，记录 ReActStep + Observation，写入 Conversation，`task.increaseTurn()`
    - 情况 C (空响应)：`task.fail()`，返回失败
    - 达到 maxTurns：`task.hitMaxTurns()`，返回失败 + 已完成 steps
  - Thought 提取：从 `AssistantMessage.getContent()` 取前 200 字符作为思考摘要
  - 工具执行：`toolRegistry.execute(toolCall, toolContext)`，结果转换为 Observation
  - 多工具顺序执行：按 tool_calls 数组顺序，每个工具结果独立写入 Conversation
- [x] 3.2 编写 M3 单元测试（使用 Mockito Mock ChatModelPort 和 ToolRegistry）
  - `ReActLoopTest`: 8 个测试全部通过
    - Final Answer 直达（mock 返回 content，无 toolCalls）
    - 单工具调用 + Final（第1轮 toolCalls，第2轮 content）
    - 多工具调用（第1轮 2 个 toolCalls）
    - 工具执行失败（ToolRegistry 返回 ToolResult.error()）
    - 未知工具（toolCalls 含未注册工具）
    - 达到 maxTurns（maxTurns=3，每轮返回 toolCalls）
    - LLM API 失败（chatModel 抛 ApiException）
    - LLM 空响应（chatModel 返回空 choices）

## M4: Agent 集成

- [x] 4.1 创建 `DefaultAgent` 默认实现 at `domain/agent/DefaultAgent.java`
  - 实现 `Agent` 接口
  - 构造器注入：`ChatModelPort` + `ToolRegistry` + `SessionAppService` + `ApprovalHandler`
  - `run(AgentRequest)` 实现：校验输入、获取/创建会话、创建 AgentTask/Conversation/ContextBuildService/ToolExecutionContext、委托 ReActLoop
- [x] 4.2 重构 `AgentAppService` at `application/service/AgentAppService.java`
  - 构造器变更为注入 `Agent` 端口（替代直接依赖 `ChatModelPort`）
  - `handleUserInput()` 内部构建 `AgentRequest`，委托 `agent.run(request)`
  - 结果转换为 `AgentResponseDTO`
- [x] 4.3 扩展 `AgentResponseDTO` at `application/dto/AgentResponseDTO.java`
  - 新增字段：`steps: List<ReActStepDTO>`, `totalTurns: int`
  - 新增工厂方法：`from(AgentResponse agentResponse)` — 将领域对象映射为 DTO
  - 新增 `ReActStepDTO` 内部类（精简版 ReActStep，不含内部实现细节）
- [x] 4.4 更新 ConsolePresenter at `interfaces/cli/ConsolePresenter.java`
  - 新增 `printReActSteps()` 方法，格式化输出 Thought / Action / Observation / Final Answer
  - 保留原有响应展示行为
- [x] 4.5 更新 `Main.java` 依赖组装
  - 创建 `DefaultAgent` 实例（注入 ChatModelPort + ToolRegistry + SessionAppService + ApprovalHandler）
  - 更新 `AgentAppService` 构造（注入 Agent）
  - 移除 `AgentAppService` 对 `ChatModelPort` 的直接依赖

## M5: 测试与验收

- [x] 5.1 完成所有单元测试 — 全量 140 测试通过，0 失败
  - `AgentRequestTest` — 8 用例
  - `ConversationTest` — 9 用例
  - `ContextBuildServiceTest` — 5 用例
  - `ReActLoopTest` — 8 用例
  - `DefaultAgentTest` — 3 用例（端到端 Mock 测试）
- [x] 5.2 执行回归测试，确保现有功能不受影响 — 全部通过
  - 已有测试：`ChatRequestTest`(5), `MessageSerializationTest`(8), `AgentSessionTest`(6), `DeepSeekResponseParserTest`(4), 工具层测试(93)
- [ ] 5.3 手动端到端验证
  - 启动 CLI，执行简单任务（`帮我查看当前目录结构`），验证 Agent 可自主调用 list_files 工具
  - 验证执行轨迹正确展示
  - 验证工具失败时 Agent 不崩溃

## 验收标准（对应 PRD 第 11 章）

- [x] 可通过 `Agent.run` 提交用户任务
- [x] Agent 可构建包含 system prompt、用户消息、历史消息和工具定义的 `ChatRequest`
- [x] Agent 可调用 `ChatModelPort.chat()`
- [x] Agent 可识别 Final Answer 并结束任务
- [x] Agent 可识别 tool_calls 并调用 `ToolRegistry.execute()`
- [x] Agent 可把 `ToolResult` 转成 Observation
- [x] Agent 可把 Observation 追加回 `Conversation`
- [x] Agent 可执行多轮 ReAct 循环
- [x] Agent 达到 maxTurns 后会停止
- [x] `AgentResponse` 包含完整执行轨迹（`ReActStep` 列表）
- [x] 工具执行失败不会导致进程崩溃
- [x] 未知工具调用会生成失败 Observation
- [x] shell 和写文件审批逻辑可通过工具层生效
- [x] 单元测试覆盖：Final Answer、单工具调用、多工具调用、工具失败、未知工具、达到最大轮次

## 任务统计

| 阶段 | 任务数 | 新增文件 | 测试文件 | 变更文件 |
|------|--------|---------|---------|---------|
| M1: Agent 基础模型 | 6 | 5 (Agent, AgentRequest, AgentResponse, ReActStep, Conversation) | 2 (AgentRequestTest, ConversationTest) | 0 |
| M2: 上下文构建 | 2 | 1 (ContextBuildService) | 1 (ContextBuildServiceTest) | 0 |
| M3: ReActLoop 核心 | 2 | 1 (ReActLoop) | 1 (ReActLoopTest) | 0 |
| M4: Agent 集成 | 5 | 1 (DefaultAgent) | 0 | 4 (AgentAppService, AgentResponseDTO, AgentCli, Main) |
| M5: 测试与验收 | 3 | 0 | 1 (DefaultAgentTest) | 0 |
| **合计** | **18** | **8 new** | **5 test** | **4 modified** |
