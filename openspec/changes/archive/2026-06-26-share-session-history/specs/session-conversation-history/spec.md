## ADDED Requirements

### Requirement: Session-scoped conversation history
The system SHALL maintain a single `Conversation` instance per `AgentSession`, created when the session starts and destroyed when the session closes. All user messages, assistant replies, and tool interaction messages within the session SHALL be appended to this shared Conversation.

#### Scenario: First user input in a session
- **WHEN** the user submits their first natural language input in a new CLI session
- **THEN** the system creates a new Conversation (if one does not exist for the session), adds the user message, and executes the ReAct loop against it

#### Scenario: Subsequent user input in the same session
- **WHEN** the user submits a second natural language input in the same CLI session
- **THEN** the system reuses the existing Conversation from the session, appends the new user message, and the LLM can reference all previous messages in the conversation history

### Requirement: Final answer preserved in conversation history
The system SHALL append each ReAct loop's final assistant answer (non-tool-call response) to the session's shared Conversation, making it available as context for subsequent user inputs.

#### Scenario: Multi-turn reference
- **WHEN** the user asks "list all Java files", the agent responds with results, and then the user asks "now search for 'TODO' in those files"
- **THEN** the LLM receives the full conversation history including the previous file listing, allowing it to understand the contextual reference "those files"

### Requirement: Conversation clear via /clear command
The system SHALL support clearing the conversation history through the `/clear` CLI command, resetting the Conversation to empty while keeping the session active and its workspace unchanged. The system MUST prompt the user for confirmation before executing the clear operation.

#### Scenario: Clear history mid-session with confirmation
- **WHEN** the user types `/clear` in an active session with existing conversation history
- **THEN** the system prompts "Are you sure you want to clear all conversation history? (y/n)" and waits for user input; if the user confirms with "y" or "yes", all non-system messages are removed from the Conversation; if the user responds with anything else, the operation is cancelled and the history remains unchanged

#### Scenario: Clear on empty conversation with confirmation
- **WHEN** the user types `/clear` in an active session with no existing conversation history
- **THEN** the system still prompts for confirmation; if confirmed, the operation completes without error (no-op); if cancelled, no change occurs

### Requirement: Conversation compaction via /compact command
The system SHALL support compressing conversation history through the `/compact` CLI command. The system MUST prompt the user for confirmation before executing. Upon confirmation, the system SHALL call the LLM with a summarization prompt over the current conversation history, then clear all history messages and append a single `UserMessage` containing the generated summary. The system behavior `SystemMessage` (set by `ContextBuildService`) SHALL NOT be modified by the compact operation.

#### Scenario: Compact non-empty history
- **WHEN** the user types `/compact` in an active session with 10+ messages in the conversation history and confirms the operation
- **THEN** the system calls the LLM to generate a structured summary preserving key decisions, file paths, code snippets, and unfinished tasks; the original history messages are cleared; the summary is appended as a `UserMessage` to the conversation history; the system behavior `SystemMessage` remains unchanged; subsequent LLM calls receive the summary as part of the regular message history

#### Scenario: Compact preserves system behavior prompt
- **WHEN** the user executes `/compact` successfully, and the `ContextBuildService` later builds a `ChatRequest`
- **THEN** the system behavior `SystemMessage` (containing workspace path, date, tool usage instructions) is still present at the front of the message list, followed by the compact summary `UserMessage` and any subsequent messages

#### Scenario: Compact empty history
- **WHEN** the user types `/compact` in an active session with no conversation history and confirms the operation
- **THEN** the system prints "Nothing to compact — conversation is already empty" and no LLM call is made

#### Scenario: Cancel compact via confirmation
- **WHEN** the user types `/compact` and responds "n" or anything other than "y"/"yes" to the confirmation prompt
- **THEN** the system prints "Cancelled" and the conversation history remains unchanged

#### Scenario: Compact LLM failure
- **WHEN** the user confirms `/compact` but the LLM summarization call fails (API error, network issue, etc.)
- **THEN** the original conversation history is preserved unchanged; the system reports the error to the user ("Compact failed: <reason>")

### Requirement: Message window retention
The system SHALL continue to apply the existing `maxMessages` sliding window (default 20) when building the message list for LLM API calls. Messages beyond the window are not sent to the LLM but remain in the Conversation for potential future use.

#### Scenario: Window overflow
- **WHEN** the conversation accumulates more than 20 messages through multiple turns and tool interactions
- **THEN** only the most recent 20 messages (plus the system message) are sent to the LLM API; older messages are retained in the Conversation but excluded from the current API call
