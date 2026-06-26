## 1. AgentSession 聚合根扩展

- [x] 1.1 在 `AgentSession` 中新增 `Conversation` 字段，构造时初始化（使用默认窗口参数 `DEFAULT_MAX_MESSAGES=20, DEFAULT_MAX_TOOL_RESULT_CHARS=4000`）
- [x] 1.2 新增 `getConversation()` 方法，返回当前 Conversation 实例
- [x] 1.3 新增 `clearConversation()` 方法，委托给 `Conversation.clear()` 并更新 `updatedAt` 时间戳

## 2. Conversation 扩展（/compact 支持）

- [x] 2.1 ~~将 `systemMessage` 字段从 `final` 改为可变~~ ← 废弃，需回退为 `final`（`SystemMessage` 保持职责单一，仅用于系统行为提示词）
- [x] 2.2 ~~新增 `compact(String summary)` 方法：存入 SystemMessage~~ ← 废弃，需重写为：清空 `historyMessages`，将摘要作为 `UserMessage` 追加到 `historyMessages`

## 3. DefaultAgent 适配

- [x] 3.1 移除 `DefaultAgent.run()` 中 `new Conversation(...)` 的局部创建逻辑（第 78-79 行）
- [x] 3.2 改为从 `sessionService.getCurrentSession().getConversation()` 获取已有 Conversation；若为 null（防御），则创建新的
- [x] 3.3 移除 `DefaultAgent` 中的 `DEFAULT_MAX_MESSAGES` 和 `DEFAULT_MAX_TOOL_RESULT_CHARS` 常量（不再需要），由 AgentSession 管理

## 4. SessionAppService 扩展

- [x] 4.1 新增 `clearConversation()` 公开方法，获取当前 session 并调用 `session.clearConversation()`
- [x] 4.2 新增 `compactConversation()` 公开方法：获取当前 session 的 Conversation，调用 LLM 生成摘要，再调用 `conversation.compact(summary)`

## 5. CommandParser 扩展（/compact 命令解析）

- [x] 5.1 在 `BuiltinCommand` 枚举中新增 `COMPACT` 值
- [x] 5.2 在 `CommandParser` 中新增 `isCompact()` 匹配方法（匹配 `/compact` 和 `compact`），路由到 `BuiltinCommand.COMPACT`

## 6. ConsolePresenter 扩展（输出格式化）

- [x] 6.1 修改 `printHelp()` 方法，在帮助信息中新增 `/compact` 命令说明
- [x] 6.2 新增 `printCompactStart()` 方法：输出 "Compacting conversation history..."
- [x] 6.3 新增 `printCompactDone(int originalMessages, int summaryChars)` 方法：输出压缩结果（原消息数 → 摘要字符数）
- [x] 6.4 新增 `printCompactCancelled()` 方法：输出 "Compact cancelled."
- [x] 6.5 新增 `printCompactEmpty()` 方法：输出 "Nothing to compact — conversation is already empty."
- [x] 6.6 新增 `printConfirm(String action)` 通用确认提示方法：输出 "Are you sure you want to $action? (y/n)"
- [x] 6.7 新增 `printCancelled()` 方法：输出 "Cancelled."

## 7. CLI 用户确认机制

- [x] 7.1 在 `AgentCli` 中新增 `confirmAction(String action, Scanner scanner)` 私有方法：打印确认提示，读取一行输入，判断是否为 `y`/`yes`（大小写不敏感），返回 boolean
- [x] 7.2 修改 `handleBuiltin(CLEAR)` 分支：先调用 `confirmAction("clear all conversation history", scanner)`，仅确认后才调用 `sessionService.clearConversation()`；取消则调用 `presenter.printCancelled()`
- [x] 7.3 新增 `handleBuiltin(COMPACT)` 分支：先调用 `confirmAction("compact conversation history", scanner)`，确认后调用 `sessionService.compactConversation()`；取消则调用 `presenter.printCancelled()`

## 8. Bug 修复：compact 摘要改为 historyMessages

- [x] 8.0a 回退 `Conversation.systemMessage` 为 `final`，删除 `setSystemMessage()` 方法
- [x] 8.0b 重写 `Conversation.compact(String summary)`：调用 `clear()` 清空历史 → 调用 `addUserMessage("[Conversation summary]:\n" + summary)` 将摘要作为普通历史消息追加
- [x] 8.0c 更新 `SessionAppService.compactConversation()`：移除对 `tempConv.setSystemMessage()` 的调用，改用 `tempConv.addUserMessage()` 注入 compact 系统提示

## 9. 测试与验证

- [x] 9.1 编写或更新 `AgentSessionTest`：验证新 session 创建时 Conversation 不为 null，`clearConversation()` 正确清空历史
- [x] 9.2 编写或更新 `DefaultAgentTest`：验证第二次调用 `run()` 时 Conversation 包含前一次的消息
- [x] 9.3 更新 `ConversationTest`：验证 `compact(summary)` 将摘要作为 UserMessage 追加到历史（而非覆盖 SystemMessage）
- [x] 9.4 编写或更新 `CommandParserTest`：验证 `/compact` 被正确解析为 `BuiltinCommand.COMPACT`
- [ ] 9.5 手动集成测试：启动 CLI，连续输入两个关联问题，验证 LLM 能引用前一轮上下文
- [ ] 9.6 手动集成测试：输入 `/clear` 后拒绝确认（输入 n），验证历史未被清除、LLM 仍能引用前文
- [ ] 9.7 手动集成测试：输入 `/clear` 后确认（输入 y），验证历史已清除、LLM 无前文记忆
- [ ] 9.8 手动集成测试：输入 `/compact` 后确认，验证历史被压缩为摘要、后续 LLM 能基于摘要回答
- [ ] 9.9 手动集成测试：输入 `/compact` 后取消，验证历史未被修改
