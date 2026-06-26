## Context

当前 `DefaultAgent.run()` 方法（第 78-80 行）每次被调用时都会创建一个全新的 `Conversation` 对象。`Conversation` 的生命周期仅限于单次 ReAct 循环执行，方法返回后即被 GC。`AgentSession` 聚合根只持有元数据（id、workspace、status、timestamps），不持有对话历史。

`ReActLoop.execute()` 已经在第 111 行将最终回答通过 `conversation.addAssistantMessage()` 写回 Conversation，但该数据随 Conversation 对象一同被丢弃。这意味着所有历史持久化的基础设施已经就绪，唯一缺失的是**将 Conversation 的生命周期从方法作用域提升到 Session 作用域**。

项目采用 DDD 分层架构，`AgentSession` 是 session 的聚合根，自然应持有属于 session 生命周期的 `Conversation`。

## Goals / Non-Goals

**Goals:**
- 同一 CLI session 内多次用户输入的对话历史对 LLM 可见
- `/clear` 命令清除对话历史但保留 session（不退出），执行前需用户确认
- `/compact` 命令调用 LLM 将对话历史压缩为摘要，替换原始消息以减少 token 占用
- ReAct 循环内的工具调用历史（Tool Calls / Tool Results）继续受 `maxMessages` 窗口限制
- 保持现有 ReAct 循环逻辑不变

**Non-Goals:**
- 不实现对话历史的持久化（磁盘存储）
- 不改变 `maxMessages` 窗口大小（保持 20）
- 不支持跨 session 的历史共享
- 不改变 System Prompt 的内容或注入方式

## Decisions

### Decision 1: Conversation 存储在 AgentSession 聚合根中

**选择**：在 `AgentSession` 中新增 `Conversation` 字段，在构造时初始化，提供 `getConversation()` 和 `clearConversation()` 方法。

**替代方案**：
- **方案 B：存储在 `SessionAppService`** — 将 Conversation 放在应用层而非领域层。缺点：破坏了 DDD 聚合根的内聚性，Conversation 的生命周期显然属于 Session。
- **方案 C：通过 `AgentRequest` 传入** — 每次调用时由调用方组装 Conversation 传入。缺点：增加了调用方的负担，且 Conversation 的管理逻辑分散到多个调用点。

**理由**：`Conversation` 的生命周期与 `AgentSession` 完全一致（session 创建时开始，session 关闭时结束）。将其放在聚合根中符合 DDD 原则，且改动最小。

### Decision 2: DefaultAgent.run() 不再创建 Conversation

**选择**：`DefaultAgent.run()` 从 `sessionService.getCurrentSession().getConversation()` 获取已有的 Conversation 实例。仅当该实例不存在（防御性编程）时才创建新的。

**理由**：消除了"每次调用创建新 Conversation"的根因。`DefaultAgent` 已经有 `sessionService` 依赖（第 32 行），无需新增注入。

### Decision 3: Conversation 仅追加用户消息，不清空重建

**选择**：`DefaultAgent.run()` 获取已有 Conversation 后仅调用 `addUserMessage()`，不调用 `clear()`。ReActLoop 执行期间追加的所有消息（tool calls、tool results、final answer）自然保留到下一轮。

**理由**：ReActLoop 已正确地将中间消息和最终回答写入 Conversation。唯一的区别是 Conversation 在多次 `run()` 调用间存活，因此这些消息对后续调用仍然可见。

### Decision 4: /clear 命令通过 SessionAppService 清除历史

**选择**：在 `SessionAppService` 中新增 `clearConversation()` 方法，委托给 `AgentSession.clearConversation()`。`AgentCli.handleBuiltin(CLEAR)` 调用该方法。

**理由**：遵循现有的分层模式——CLI 层通过应用服务操作领域对象，不直接访问聚合根内部。

### Decision 5: /compact 通过 LLM 摘要压缩历史

**选择**：`/compact` 命令调用 LLM（使用 `ChatModelPort`）生成对话摘要，将当前所有历史消息压缩为一条 `UserMessage`（内容为摘要文本）追加到 `historyMessages` 中，并清空原始历史消息列表。摘要 prompt 要求保留关键决策、文件路径、代码片段和未完成任务等信息。

**重要**：摘要作为普通 `UserMessage` 存入 `historyMessages`，而非覆盖 `SystemMessage`。`SystemMessage` 字段保持 `final`（仅用于 `ContextBuildService` 注入的系统行为提示词），与 compact 摘要职责分离。

**替代方案**：
- **方案 B：简单截断** — 只保留最近 N 条消息，丢弃旧消息。缺点：丢失旧消息中的关键上下文。
- **方案 C：纯客户端压缩** — 在本地用规则（如保留每个用户的原始输入、丢弃工具调用细节）压缩。缺点：无法理解语义，可能丢弃重要信息。
- **方案 D：摘要存入 SystemMessage 字段**（初始方案，已废弃） — 覆盖 `SystemMessage` 会破坏系统提示词的语义，且需要将 `systemMessage` 改为可变。`SystemMessage` 的职责是设定 Agent 行为约束，不应被 compact 操作污染。

**理由**：摘要作为 `UserMessage` 进入 `historyMessages`，享受与其他消息一致的滑动窗口语义；`SystemMessage` 保持职责单一（仅系统行为提示词）。虽然会触发一次额外 API 调用，但后续每轮都节省大量 token，总体收益为正。

### Decision 6: /clear 和 /compact 需用户二次确认

**选择**：在 `AgentCli` 中，`/clear` 和 `/compact` 命令执行前先输出确认提示 `"Are you sure? (y/n)"`，读取用户输入后仅在回答为 `y`/`yes` 时执行操作。其他输入取消操作并给出提示。

**理由**：这两个操作具有破坏性（`/clear` 不可逆地删除全部历史，`/compact` 用摘要替换原始消息），用户确认是防止误操作的标准 UX 实践。实现简单，不需要修改领域层或应用层。

## Risks / Trade-offs

- **[上下文膨胀风险]**：多轮对话可能导致消息数迅速超过 `maxMessages=20` 的窗口 → **缓解**：现有的 `Conversation.getMessages()` 已实现滑动窗口（只保留最近 N 条），此行为不变
- **[Token 费用增加]**：后续每轮 LLM 调用都携带完整历史，API 费用增加 → **缓解**：这是多轮对话的自然代价；`maxMessages` 窗口限制了上限；用户可通过 `/clear` 主动重置
- **[工具结果污染]**：前一轮的工具调用结果（可能很大）会留在历史中 → **缓解**：`TextTruncator` 已截断超过 4000 字符的工具结果，且窗口限制确保旧结果被逐出
- **[摘要质量风险]**：`/compact` 生成的摘要可能遗漏关键信息（如错误堆栈、精确文件路径）→ **缓解**：摘要 prompt 显式要求保留代码路径、错误信息和未完成任务；用户可在 compact 后继续追问细节
- **[compact 失败处理]**：如果 LLM 调用失败（网络问题、额度耗尽），`/compact` 操作可能中断 → **缓解**：失败时保留原始 Conversation 不变，向用户报告错误；compact 是纯优化操作，失败不影响正常使用

## Open Questions

- 是否需要在 `/session` 命令的输出中显示当前对话历史的轮数（消息数）？—— 可在实现过程中根据用户体验决定
- ~~`/compact` 摘要是否应作为 System Message 还是普通 User Message 注入？~~ **已决议**：作为 `UserMessage` 存入 `historyMessages`，`SystemMessage` 仅用于系统行为提示词（Decision 5）
