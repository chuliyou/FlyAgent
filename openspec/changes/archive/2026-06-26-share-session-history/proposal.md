## Why

当前 CLI session 中，每次用户输入都会创建一个全新的 `Conversation` 对象（`DefaultAgent.run()` 第 78-80 行），导致同一 session 内的多次用户输入之间无法共享对话历史。用户每次提问都是"全新对话"，LLM 无法引用之前的交互上下文，严重限制了多轮对话能力。这是 Agent CLI 的核心体验缺陷——用户期望在一个 session 里进行多轮连贯对话。

## What Changes

- **将 `Conversation` 的生命周期从"单次 ReAct 调用"提升到"整个 Session"**：`Conversation` 不再在 `DefaultAgent.run()` 内局部创建，而是由 `AgentSession` 持有并在多次 `run()` 调用间复用
- **ReAct 最终回答写回对话历史**：每次 ReAct 循环完成后的最终 Assistant 回复被追加到 `Conversation`，作为下一轮对话的上下文
- **`/clear` 命令真正生效**：清除当前 session 的对话历史但保留 session 本身（不退出）；执行前需用户二次确认
- **新增 `/compact` 命令**：调用 LLM 将对话历史压缩为结构化摘要，替换原始消息以释放 token 占用，同时保留关键上下文；执行前需用户二次确认
- **保留消息窗口策略**：沿用现有的 `maxMessages=20` 滑动窗口，防止上下文过长

## Capabilities

### New Capabilities
- `session-conversation-history`: 同一 CLI session 内的多次用户输入共享对话历史，每次 ReAct 完成的最终回答自动追加为 Assistant 消息，LLM 在后续轮次中可以引用之前的所有交互；提供 `/clear`（清空历史）和 `/compact`（压缩历史为摘要）两种会话管理命令，二者执行前均需用户二次确认

### Modified Capabilities
<!-- 无已有 spec 需要修改 -->

## Impact

- **受影响代码**：
  - `domain/session/AgentSession.java` — 新增 `Conversation` 字段
  - `domain/agent/DefaultAgent.java` — 不再自行创建 `Conversation`，改为从 session 获取
  - `application/service/AgentAppService.java` — 在 `handleUserInput` 中从 session 提取 conversation 并传入 agent
  - `interfaces/cli/AgentCli.java` — `/clear` 和 `/compact` 命令接入，包含用户确认交互
  - `interfaces/cli/CommandParser.java` — 新增 `COMPACT` 命令类型解析
  - `domain/agent/Conversation.java` — 新增 `compact()` 方法（压缩历史为摘要）
  - `domain/agent/AgentRequest.java` — 可能需要新增 `conversation` 字段或改为从 session 传入

- **不受影响**：工具执行、ReAct 循环逻辑、LLM 调用、会话生命周期管理
