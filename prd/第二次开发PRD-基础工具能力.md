# FlyAgent 第二次开发 PRD：基础工具能力

版本：v0.1  
对应一期 PRD：`prd/一期PRD-Claude-Code风格CLI-Agent.md`  
对应第一次开发：项目搭建与 DeepSeek API 客户端  
技术栈：Java 17 + Maven  
开发定位：在已完成消息格式、工具格式和 DeepSeek `chat` 方法的基础上，实现 FlyAgent 最基础的本地工具能力。

## 1. 背景

第一次开发完成后，FlyAgent 已具备项目基础结构、DeepSeek API 客户端、消息格式定义、工具格式定义和完整 `chat` 方法。第二次开发需要补齐 Agent 可执行本地动作的基础工具层，让后续 ReAct Loop 能够通过 Action 调用真实工具，并把工具结果作为 Observation 返回模型。

第二次开发的核心闭环：

```text
工具定义 -> 工具注册 -> 参数校验 -> 安全检查 -> 执行工具 -> 返回 ToolResult -> 形成 Observation
```

本次开发不要求完成完整 Agent Loop，但工具接口和返回格式必须能够直接被后续 ReAct Loop 复用。

## 2. 开发目标

第二次开发交付 5 个基础工具：

1. 读取文件：`read_file`
2. 写入文件：`write_file`
3. 列出目录：`list_files`
4. 执行 shell 命令：`run_shell`
5. 创建项目结构：`create_project_structure`

同时需要完成：

1. 工具统一接口。
2. 工具注册表。
3. 工具调用参数结构。
4. 工具执行结果结构。
5. workspace 安全边界校验。
6. shell 命令执行确认接口预留。
7. 基础单元测试。

## 3. 非目标

第二次开发暂不实现：

1. 完整 ReAct Agent Loop。
2. DeepSeek 自动选择工具后的连续多轮执行。
3. MySQL 持久化工具调用记录。
4. Redis 缓存工具运行态。
5. Git diff 展示。
6. 文件 patch 局部替换工具。
7. 复杂 shell 沙箱。
8. 自动修复测试失败。
9. 插件化工具加载。

## 4. 目标用户

主要用户：

1. FlyAgent 后续 ReAct Loop。
2. FlyAgent CLI 层。
3. 希望通过 Java API 调用本地工具能力的开发者。

典型场景：

1. Agent 读取 `pom.xml` 分析项目。
2. Agent 列出当前工作目录结构。
3. Agent 写入一个新的 Java 类。
4. Agent 执行 `mvn test` 并读取输出。
5. Agent 创建标准 Maven 项目目录。

## 5. 功能需求

### 5.1 工具统一接口

必须定义统一工具接口：

```java
public interface Tool {
    String name();

    String description();

    ToolDefinition definition();

    ToolResult execute(ToolCall call, ToolExecutionContext context);
}
```

要求：

1. 所有工具通过统一接口执行。
2. 工具名称必须唯一。
3. 工具定义需要能转换为 DeepSeek function tool 格式。
4. 工具执行异常不能直接导致进程崩溃，应转换为 `ToolResult`。

### 5.2 ToolCall

工具调用结构：

```java
public class ToolCall {
    private String id;
    private String name;
    private Map<String, Object> arguments;
}
```

要求：

1. `id` 可为空；为空时由调用方或工具层生成。
2. `name` 必须匹配已注册工具。
3. `arguments` 保存模型传入的工具参数。

### 5.3 ToolResult

工具执行结果结构：

```java
public class ToolResult {
    private boolean success;
    private String content;
    private String error;
    private Map<String, Object> metadata;
}
```

要求：

1. 成功时 `success=true`，`content` 为可作为 Observation 的文本。
2. 失败时 `success=false`，`error` 为用户可读错误。
3. `metadata` 可记录路径、耗时、输出长度、退出码等结构化信息。
4. 超长结果必须截断，并在 `metadata` 中记录 `truncated=true`。

### 5.4 ToolRegistry

必须实现工具注册表：

```java
public class ToolRegistry {
    public void register(Tool tool);
    public Optional<Tool> find(String name);
    public List<ToolDefinition> definitions();
    public ToolResult execute(ToolCall call, ToolExecutionContext context);
}
```

要求：

1. 启动时注册 5 个基础工具。
2. 重复工具名注册应直接失败。
3. 未知工具调用应返回失败 `ToolResult`。
4. `definitions()` 返回 DeepSeek 可用的工具定义列表。

### 5.5 ToolExecutionContext

工具执行上下文：

```java
public class ToolExecutionContext {
    private Path workspace;
    private Duration timeout;
    private boolean requireShellApproval;
    private boolean requireWriteApproval;
    private ApprovalHandler approvalHandler;
}
```

要求：

1. 所有文件和目录操作必须限制在 `workspace` 内。
2. shell 命令默认以 `workspace` 作为工作目录。
3. 写文件和执行 shell 命令需要预留审批处理。

## 6. 基础工具需求

### 6.1 read_file

用途：读取 workspace 内文本文件内容。

参数：

| 参数 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `path` | string | 是 | 无 | 相对 workspace 的文件路径 |
| `maxChars` | integer | 否 | 20000 | 最大读取字符数 |

行为要求：

1. 禁止读取 workspace 外文件。
2. 文件不存在时返回失败。
3. 目录路径不能作为文件读取。
4. 默认按 UTF-8 读取。
5. 超过 `maxChars` 时截断并提示。
6. 返回内容应包含相对路径和文件正文。

成功返回示例：

```text
File: pom.xml
Chars: 4812

<project>...</project>
```

### 6.2 write_file

用途：在 workspace 内创建或覆盖文本文件。

参数：

| 参数 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `path` | string | 是 | 无 | 相对 workspace 的文件路径 |
| `content` | string | 是 | 无 | 写入内容 |
| `overwrite` | boolean | 否 | false | 是否允许覆盖已有文件 |

行为要求：

1. 禁止写入 workspace 外文件。
2. 父目录不存在时允许自动创建。
3. 文件已存在且 `overwrite=false` 时返回失败。
4. 写入前调用审批接口；审批拒绝时不写入。
5. 写入成功后返回路径、写入字符数和是否覆盖。

成功返回示例：

```text
File written: src/main/java/com/demo/App.java
Chars: 326
Overwritten: false
```

### 6.3 list_files

用途：列出 workspace 内目录结构。

参数：

| 参数 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `path` | string | 否 | `.` | 相对 workspace 的目录路径 |
| `maxDepth` | integer | 否 | 3 | 最大目录深度 |
| `includeHidden` | boolean | 否 | false | 是否包含隐藏文件 |

行为要求：

1. 禁止列出 workspace 外目录。
2. 默认忽略 `.git`、`target`、`node_modules`、`.idea`。
3. 输出相对路径。
4. 目录不存在时返回失败。
5. 输出过长时截断。

输出示例：

```text
.
├── pom.xml
├── src
│   ├── main
│   └── test
└── README.md
```

### 6.4 run_shell

用途：在 workspace 内执行 shell 命令。

参数：

| 参数 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `command` | string | 是 | 无 | 待执行命令 |
| `timeoutSeconds` | integer | 否 | 120 | 超时时间 |

行为要求：

1. shell 命令执行前必须调用审批接口。
2. 命令工作目录必须是 workspace。
3. 捕获 stdout、stderr、exitCode。
4. 超时后终止进程并返回失败。
5. 危险命令需要拒绝或二次确认。
6. 输出默认最多保留 30000 字符。

危险命令示例：

```text
rm -rf /
del /s
git reset --hard
format
shutdown
reboot
```

成功返回示例：

```text
Command: mvn test
ExitCode: 0

STDOUT:
...

STDERR:
...
```

### 6.5 create_project_structure

用途：根据项目类型创建基础目录结构和初始文件。

参数：

| 参数 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `projectType` | string | 是 | 无 | 项目类型，例如 `maven-java` |
| `basePath` | string | 否 | `.` | 创建位置 |
| `groupId` | string | 否 | `com.example` | Maven groupId |
| `artifactId` | string | 否 | `demo` | Maven artifactId |
| `packageName` | string | 否 | 根据 groupId 推导 | Java 包名 |
| `overwrite` | boolean | 否 | false | 是否覆盖已有文件 |

一期第二次开发至少支持：

```text
maven-java
```

`maven-java` 需要创建：

```text
pom.xml
src/main/java/{package}/App.java
src/test/java/{package}/AppTest.java
README.md
```

行为要求：

1. 禁止在 workspace 外创建。
2. 已存在文件默认不覆盖。
3. 创建前调用写入审批接口。
4. 返回创建文件列表。
5. 部分失败时返回失败，并说明已创建和失败的文件。

## 7. 安全需求

### 7.1 WorkspaceGuard

所有路径必须通过统一安全校验：

```java
Path resolved = workspace.resolve(inputPath).normalize();
if (!resolved.startsWith(workspace)) {
    throw new SecurityException("Path is outside workspace");
}
```

要求：

1. 禁止 `../` 路径逃逸。
2. 禁止绝对路径越权。
3. 禁止通过符号链接逃逸 workspace。
4. 工具输出优先展示相对路径。

### 7.2 ApprovalHandler

审批接口：

```java
public interface ApprovalHandler {
    boolean approve(ApprovalRequest request);
}
```

审批请求：

```java
public class ApprovalRequest {
    private String actionType;
    private String summary;
    private Map<String, Object> details;
}
```

第二次开发可提供两个实现：

1. `AlwaysApproveHandler`：测试使用。
2. `ConsoleApprovalHandler`：CLI 使用，后续接入正式交互。

## 8. ReAct 集成要求

第二次开发的工具结果需要能直接作为 ReAct Observation。

要求：

1. `ToolResult.content` 应是自然语言可读文本。
2. `ToolResult.metadata` 保留结构化信息。
3. 每个工具定义都能转换为 DeepSeek function tool。
4. 工具失败也要返回 Observation，便于模型下一轮修正。

示例：

```text
Action: read_file {"path":"pom.xml"}
Observation: File: pom.xml
Chars: 4812
...
```

## 9. 技术约束

1. Java 17。
2. Maven。
3. 不依赖 LangChain、AutoGPT、Semantic Kernel 等 Agent 框架。
4. 文件操作优先使用 `java.nio.file`。
5. shell 执行使用 `ProcessBuilder`。
6. JSON Schema 工具定义复用第一次开发的工具格式。
7. 工具层不得直接依赖 DeepSeek 客户端。

## 10. 推荐工程结构

```text
src/main/java/com/flyagent/
  domain/
    model/
      tool/
        ToolCall.java
        ToolResult.java
        ToolDefinition.java
        ToolParameterSchema.java
    service/
      WorkspaceGuard.java
      CommandSafetyChecker.java
  infrastructure/
    tools/
      Tool.java
      ToolRegistry.java
      ToolExecutionContext.java
      ApprovalHandler.java
      ApprovalRequest.java
      AlwaysApproveHandler.java
      ConsoleApprovalHandler.java
      ReadFileTool.java
      WriteFileTool.java
      ListFilesTool.java
      RunShellTool.java
      CreateProjectStructureTool.java
    util/
      TextTruncator.java
      PathUtils.java
```

## 11. 验收标准

第二次开发完成后，应满足：

1. `read_file` 可以读取 workspace 内文本文件。
2. `read_file` 不能读取 workspace 外文件。
3. `write_file` 可以创建新文件。
4. `write_file` 默认不覆盖已有文件。
5. `list_files` 可以输出目录结构。
6. `list_files` 默认忽略常见无关目录。
7. `run_shell` 可以执行安全命令并返回 stdout、stderr、exitCode。
8. `run_shell` 执行前必须经过审批接口。
9. `run_shell` 对危险命令有拦截或二次确认机制。
10. `create_project_structure` 可以创建 `maven-java` 项目结构。
11. 所有文件路径都不能越过 workspace。
12. 所有工具失败时返回可读 `ToolResult`，而不是直接抛到 CLI。
13. `ToolRegistry` 可以注册并执行 5 个基础工具。
14. 5 个工具都能输出 DeepSeek function tool 定义。
15. 单元测试覆盖正常路径、越权路径、参数缺失、执行失败等场景。

## 12. 测试用例

### 12.1 read_file

1. 读取存在文件，返回内容。
2. 读取不存在文件，返回失败。
3. 读取目录，返回失败。
4. 使用 `../` 逃逸，返回失败。
5. 大文件读取触发截断。

### 12.2 write_file

1. 创建新文件成功。
2. 父目录不存在时自动创建。
3. 已存在文件且 `overwrite=false`，返回失败。
4. 已存在文件且 `overwrite=true`，审批通过后覆盖。
5. 审批拒绝时不写入。
6. workspace 外路径写入失败。

### 12.3 list_files

1. 列出根目录。
2. 列出指定子目录。
3. 限制最大深度。
4. 默认忽略 `.git` 和 `target`。
5. workspace 外路径失败。

### 12.4 run_shell

1. 执行 `echo hello` 成功。
2. 执行失败命令返回非 0 exitCode。
3. 审批拒绝时不执行。
4. 危险命令被拒绝。
5. 超时命令被终止。

### 12.5 create_project_structure

1. 创建 `maven-java` 结构成功。
2. 已存在文件不覆盖。
3. `overwrite=true` 时审批后覆盖。
4. 非法 projectType 返回失败。
5. workspace 外 basePath 返回失败。

## 13. 开发里程碑

### M1：工具基础模型

交付：

1. `Tool` 接口。
2. `ToolCall`。
3. `ToolResult`。
4. `ToolExecutionContext`。
5. `ToolRegistry`。

### M2：安全与审批

交付：

1. `WorkspaceGuard`。
2. `CommandSafetyChecker`。
3. `ApprovalHandler`。
4. `AlwaysApproveHandler`。
5. `ConsoleApprovalHandler`。

### M3：文件与目录工具

交付：

1. `ReadFileTool`。
2. `WriteFileTool`。
3. `ListFilesTool`。
4. 相关单元测试。

### M4：Shell 与项目结构工具

交付：

1. `RunShellTool`。
2. `CreateProjectStructureTool`。
3. 相关单元测试。

### M5：集成与验收

交付：

1. 工具注册集成。
2. DeepSeek function tool definition 输出。
3. 工具调用手动测试说明。
4. 完整测试通过。

## 14. 风险与应对

| 风险 | 影响 | 应对 |
| --- | --- | --- |
| 路径越权 | 可能读写 workspace 外文件 | 所有工具统一经过 WorkspaceGuard |
| shell 命令危险 | 可能破坏用户环境 | 审批 + 高危命令拦截 |
| 输出过长 | 影响模型上下文 | 统一 TextTruncator 截断 |
| 编码不一致 | 中文或特殊字符读取异常 | 默认 UTF-8，错误时返回明确提示 |
| 项目结构覆盖用户文件 | 数据丢失 | 默认不覆盖，覆盖必须审批 |

## 15. 总结

第二次开发要交付 FlyAgent 的最小本地工具层。完成后，FlyAgent 将具备读取文件、写入文件、列出目录、执行 shell 命令和创建 Maven Java 项目结构的能力，并且这些工具可以通过统一定义暴露给 DeepSeek，作为后续 ReAct Loop 的 Action 执行基础。
