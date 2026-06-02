# FlyAgent 一期 PRD：Java CLI Coding Agent

版本：v0.2  
技术栈：Java 17 + Maven  
产品定位：不依赖第三方 Agent 框架，从零手写一个采用 ReAct 模式的 Claude Code 风格 CLI 编程 Agent MVP。

## 1. 产品背景

开发者希望通过命令行与 AI Agent 协作完成代码任务，例如阅读项目、分析问题、修改文件、运行测试、解释报错等。现有 Agent 工具能力强，但往往依赖特定平台或框架。

FlyAgent 的目标是用 Java 17 + Maven 从零搭建一个可扩展的 CLI Agent，为后续支持多模型、多工具、多轮任务执行、插件系统打基础。

一期目标不是做完整 Claude Code 替代品，而是基于 ReAct 模式实现一个可工作的最小闭环：

```text
用户输入任务 -> Thought 思考 -> Action 调用工具 -> Observation 观察结果 -> Final Answer 输出结果 -> 支持继续对话
```

## 2. 一期目标

一期交付一个可运行的 CLI Agent，具备以下能力：

1. 在终端中启动交互式会话。
2. 支持用户用自然语言提出代码相关任务。
3. 支持 Agent 调用本地工具：
   - 查看当前目录。
   - 读取文件。
   - 搜索文件内容。
   - 执行 shell 命令。
   - 修改或创建文件。
4. 支持多轮上下文记忆。
5. 支持命令执行前的用户确认机制。
6. 支持基础配置，例如 API Key、模型名、工作目录。
7. 一期 Agent 执行模式采用 ReAct，即 Thought、Action、Observation、Final Answer 的有限循环。
8. 输出 Agent 的思考摘要、执行动作、观察结果和最终结果。
9. 不依赖 LangChain、AutoGPT、Semantic Kernel 等第三方 Agent 框架。

## 3. 非目标

一期暂不实现：

1. 图形界面。
2. 多 Agent 协作。
3. 插件市场。
4. 自动 Git commit。
5. 长期记忆数据库。
6. 复杂任务规划器。
7. 远程开发环境。
8. IDE 插件。
9. 完整 MCP 协议兼容。
10. 自主无限循环执行。
11. 复杂多分支任务规划器，ReAct 只作为一期的轻量执行循环。

## 4. 目标用户

主要用户：

1. Java 后端开发者。
2. 希望在命令行中使用 AI 辅助编码的个人开发者。
3. 想学习 Agent 原理并自研工具链的技术团队。

典型场景：

1. “帮我看一下这个项目结构。”
2. “解释这个报错。”
3. “帮我修改这个类，加一个参数校验。”
4. “运行测试并修复失败。”
5. “帮我生成一个 Maven 项目的基础代码。”

## 5. 核心用户流程

### 5.1 启动会话

用户执行：

```bash
java -jar coding-agent.jar
```

系统进入交互模式：

```text
Coding Agent started.
Workspace: D:\projects\demo
>
```

用户输入：

```text
帮我分析这个项目是做什么的
```

Agent 执行：

1. Thought：判断需要先了解目录和关键文件。
2. Action：调用 `list_files` 获取目录结构。
3. Observation：观察目录结果，定位 `pom.xml`、`README.md` 等关键文件。
4. Action：调用 `read_file` 读取关键文件。
5. Observation：观察文件内容。
6. Final Answer：输出项目分析结果。

### 5.2 执行命令前确认

用户输入：

```text
帮我运行测试
```

Agent 判断需要执行：

```bash
mvn test
```

CLI 显示：

```text
Agent wants to run:
mvn test

Allow? [y/N]
```

用户确认后执行，并把结果反馈给模型。

该流程在 ReAct 模式中表现为：

1. Thought：判断运行测试需要执行 shell 命令。
2. Action：请求调用 `run_shell`，参数为 `mvn test`。
3. Approval：CLI 展示命令并等待用户确认。
4. Observation：读取命令输出、退出码和错误信息。
5. Final Answer：解释测试结果或失败原因。

### 5.3 修改文件

用户输入：

```text
帮我给 UserService 的 createUser 方法增加邮箱格式校验
```

Agent 执行：

1. Thought：判断需要定位 `UserService`。
2. Action：调用 `search_text` 搜索类或方法。
3. Observation：观察搜索结果，选择目标文件。
4. Action：调用 `read_file` 读取相关文件。
5. Thought：生成最小修改方案。
6. Action：调用 `patch_file` 或 `write_file`。
7. Approval：CLI 展示变更摘要并等待用户确认。
8. Observation：观察文件修改结果。
9. Final Answer：输出变更摘要，并建议或执行测试。

## 6. 功能需求

### 6.1 CLI 交互

必须支持：

1. 交互式输入。
2. 多轮对话。
3. `exit` / `quit` 退出。
4. `clear` 清空当前会话上下文。
5. `pwd` 显示当前工作目录。
6. `help` 显示可用命令。

建议一期支持：

```text
> 帮我阅读这个项目
> /help
> /clear
> /exit
```

### 6.2 LLM 调用模块

需要实现一个模型适配层，避免业务逻辑直接依赖某个模型 API。

接口示例：

```java
public interface ChatModel {
    ChatResponse chat(ChatRequest request);
}
```

一期建议先支持 OpenAI-compatible API。

配置项示例：

```properties
agent.api.base-url=https://api.openai.com/v1
agent.api.key=${API_KEY}
agent.model=gpt-4.1
```

后续可以扩展到 Claude、Gemini、本地模型等。

### 6.3 ReAct Agent 循环

一期 Agent 采用有限轮次 ReAct 执行循环。ReAct 的核心是让 Agent 在每一轮显式经历：

```text
Thought：基于用户任务和上下文判断下一步
Action：选择一个工具并给出参数，或决定直接回答
Observation：读取工具执行结果
Final Answer：任务完成时输出最终回答
```

一期完整循环：

```text
用户输入
  ↓
组装上下文
  ↓
调用模型
  ↓
模型返回：Thought + Action 或 Final Answer
  ↓
如果是 Action，则执行工具
  ↓
把 Observation 返回模型
  ↓
继续下一轮 Thought
  ↓
直到模型给出 Final Answer，或达到最大轮次
```

默认最大轮次：`8`

通过有限轮次避免 Agent 无限执行。

### 6.4 ReAct 输出要求

一期 CLI 需要将 Agent 执行过程以可解释方式输出，但不要求暴露完整模型推理链路。建议输出“思考摘要”而不是完整隐藏推理。

展示格式示例：

```text
Thought: 需要先查看项目结构和 Maven 配置。
Action: list_files {"path": ".", "maxDepth": 3}
Observation: 找到 pom.xml、src/main/java、src/test/java。
Action: read_file {"path": "pom.xml"}
Observation: 已读取 4,812 字符。
Final Answer: 这是一个 Java Maven 项目...
```

要求：

1. `Thought` 展示为简短思考摘要，避免输出冗长推理链。
2. `Action` 必须包含工具名和关键参数。
3. `Observation` 必须来自真实工具结果，不允许模型伪造。
4. `Final Answer` 必须在任务结束时输出。
5. 达到最大轮次时，应输出已完成的 Action 和最后 Observation。

### 6.5 工具系统

一期工具不使用第三方 Agent 框架，自己定义工具协议。

核心接口：

```java
public interface Tool {
    String name();
    String description();
    ToolResult execute(ToolCall call);
}
```

一期工具列表：

| 工具 | 说明 |
| --- | --- |
| `list_files` | 列出目录文件 |
| `read_file` | 读取文件内容 |
| `search_text` | 在工作目录中搜索文本 |
| `write_file` | 写入或创建文件 |
| `patch_file` | 局部修改文件 |
| `run_shell` | 执行 shell 命令 |

工具执行必须限制在当前 workspace 内。

工具调用必须由 ReAct 的 `Action` 驱动，不允许绕过 Agent Loop 直接执行。

### 6.6 文件安全

必须实现：

1. 禁止访问 workspace 外部文件。
2. 写文件前显示变更摘要。
3. 执行 shell 命令前需要确认。
4. 危险命令默认拒绝或二次确认，例如：
   - `rm -rf`
   - `del /s`
   - `git reset --hard`
   - `format`
   - `shutdown`

一期可以采用简单规则拦截。

### 6.7 上下文管理

一期上下文包括：

1. 系统提示词。
2. 用户历史消息。
3. Agent 回复。
4. 工具调用结果。
5. 当前 workspace 信息。
6. ReAct 步骤记录，包括 Thought 摘要、Action、Observation、Final Answer。

需要控制上下文长度。

策略：

1. 保留最近 N 轮对话。
2. 工具结果过长时截断。
3. 文件读取默认限制字符数，例如 20,000 字符。
4. shell 输出默认限制字符数，例如 30,000 字符。
5. ReAct 步骤过多时，只保留最近若干轮完整步骤和更早步骤摘要。

### 6.8 配置管理

支持配置文件：

```text
.agent/config.properties
```

示例：

```properties
api.baseUrl=https://api.openai.com/v1
api.key=your-api-key
model=gpt-4.1
workspace=.
maxTurns=8
requireShellApproval=true
```

也支持环境变量覆盖：

```bash
AGENT_API_KEY
AGENT_MODEL
AGENT_BASE_URL
```

优先级：

```text
命令行参数 > 环境变量 > 配置文件 > 默认值
```

## 7. 技术方案

### 7.1 技术栈

| 类型 | 选择 |
| --- | --- |
| Java | Java 17 |
| 构建工具 | Maven |
| CLI | 原生 `Scanner` / `Console`，一期不强依赖 Picocli |
| HTTP | Java 17 `HttpClient` |
| JSON | 可使用 Jackson；如果严格减少依赖，可先手写轻量 JSON，但不推荐 |
| 日志 | Java Logging 或 SLF4J |
| 测试 | JUnit 5 |

说明：不依赖第三方 Agent 框架，但可以使用通用工程库，例如 Jackson、JUnit。

### 7.2 推荐项目结构

```text
coding-agent/
  pom.xml
  src/main/java/com/example/agent/
    Main.java
    cli/
      AgentCli.java
      CommandParser.java
    core/
      Agent.java
      AgentLoop.java
      ReActLoop.java
      ReActStep.java
      Conversation.java
      Message.java
      ToolCall.java
      ToolResult.java
    model/
      ChatModel.java
      OpenAiCompatibleChatModel.java
      ChatRequest.java
      ChatResponse.java
    tools/
      Tool.java
      ListFilesTool.java
      ReadFileTool.java
      SearchTextTool.java
      WriteFileTool.java
      PatchFileTool.java
      RunShellTool.java
      ToolRegistry.java
    config/
      AgentConfig.java
      ConfigLoader.java
    safety/
      WorkspaceGuard.java
      CommandSafetyChecker.java
      ApprovalService.java
  src/test/java/com/example/agent/
```

## 8. 一期验收标准

一期完成后，应满足：

1. 可以通过 Maven 打包运行：

```bash
mvn clean package
java -jar target/coding-agent.jar
```

2. 可以进入 CLI 会话。
3. 可以向模型发送用户问题并返回回答。
4. Agent 采用 ReAct 模式执行任务，至少能展示 Thought 摘要、Action、Observation、Final Answer。
5. Agent 可以读取当前项目文件并总结项目结构。
6. Agent 可以搜索代码。
7. Agent 可以在用户确认后执行 `mvn test`。
8. Agent 可以创建或修改文件。
9. 文件读写不能越过 workspace。
10. 命令执行前必须展示命令并等待用户确认。
11. 工具 Observation 必须来自真实工具执行结果。
12. 出错时有可读错误信息，而不是直接崩溃。

## 9. 里程碑

### M1：基础 CLI 与配置

交付：

1. Maven 项目初始化。
2. CLI 交互循环。
3. 配置加载。
4. 基础日志和错误处理。

### M2：模型调用

交付：

1. OpenAI-compatible API 调用。
2. 消息上下文管理。
3. 普通问答能力。

### M3：工具系统

交付：

1. Tool 接口。
2. ToolRegistry。
3. 文件读取、目录遍历、文本搜索工具。

### M4：ReAct Agent Loop

交付：

1. ReAct Prompt。
2. 模型返回 Thought + Action 或 Final Answer。
3. Agent 根据 Action 执行工具。
4. 工具结果作为 Observation 回传模型。
5. ReAct 步骤记录和展示。
6. 最终回答生成。

### M5：文件修改与命令执行

交付：

1. 写文件工具。
2. patch 文件工具。
3. shell 执行工具。
4. 用户确认机制。
5. 安全规则。

### M6：测试与打包

交付：

1. 单元测试。
2. 端到端手动测试。
3. fat jar 或可运行 jar。
4. README。

## 10. 一期成功指标

1. 用户可以在一个真实 Java Maven 项目中让 Agent 读取项目并解释结构。
2. 用户可以让 Agent 定位某个类或方法。
3. 用户可以让 Agent 修改一个简单 bug。
4. 用户可以让 Agent 运行测试并解释失败原因。
5. 一次任务最多经过 8 轮工具调用即可结束。
6. 出现危险操作时，Agent 不会自动执行。
7. 用户可以看到清晰的 ReAct 执行轨迹，包括思考摘要、动作、观察和最终回答。

## 11. 后续二期方向

二期可以扩展：

1. 更强的 patch 算法。
2. Git diff 展示与回滚。
3. 多模型切换。
4. Token 预算管理。
5. 项目索引。
6. 长任务计划器。
7. 插件式工具系统。
8. MCP 协议支持。
9. 非交互模式：

```bash
agent "帮我修复测试失败"
```

10. 自动生成提交信息。

## 12. 一期产品定位总结

一期要做的是一个可控、安全、可解释的 CLI Coding Agent MVP。

它的核心不是“完全自主编码”，而是先完成最重要的闭环：

```text
对话理解 -> Thought -> Action -> 必要时用户确认 -> Observation -> Final Answer
```

只要这个 ReAct 闭环稳定，后续增强规划能力、代码索引、插件系统和多模型支持都会比较自然。
