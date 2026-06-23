# Design: Add Tool Capabilities

## Architecture Overview

### Layer Placement

在现有六边形架构中，新增组件分布如下：

```
Interfaces (CLI)
    │
Application (AgentAppService)
    │
Domain ────────────────────────────
  domain.tool (★ 本次扩展)
    Tool (接口/端口)
    ToolResult (值对象)
    ToolRegistry (接口/端口)
    ToolExecutionContext (值对象)
    WorkspaceGuard (领域服务)
    CommandSafetyChecker (领域服务)
    ApprovalHandler (接口/端口)
    ApprovalRequest (值对象)
  domain.session (引用 Workspace)
  domain.chat (引用 ToolDefinition)
    │
Infrastructure ────────────────────
  infrastructure.tools (★ 本次新增)
    ToolRegistryImpl
    ReadFileTool, WriteFileTool, ListFilesTool
    RunShellTool, CreateProjectStructureTool
    ToolArgumentParser, TextTruncator
    AlwaysApproveHandler, ConsoleApprovalHandler
```

### Dependency Direction

```
interfaces.cli → application.service
application.service → domain.tool (Tool, ToolRegistry, ToolResult)
infrastructure.tools → domain.tool (implements Tool, ToolRegistry, ApprovalHandler)
domain.tool → domain.session (Workspace)
domain.tool → common (exceptions)
```

Tool 实现在 infrastructure 层，只依赖 domain 层接口和模型，不感知 DeepSeek 客户端等具体 infrastructure 实现。

## Domain Model

### Tool Interface (domain.tool.Tool)

```java
public interface Tool {
    String name();
    String description();
    ToolDefinition definition();
    ToolResult execute(ToolCall call, ToolExecutionContext context);
}
```

- `definition()` 返回现有 `ToolDefinition`，零适配对接 DeepSeek function calling
- `execute()` 内部 catch 所有异常，转为 `ToolResult.error()`

### ToolResult (domain.tool.ToolResult)

Value object with factory methods:

- `ToolResult.success(String content)` / `ToolResult.success(String content, Map<String,Object> metadata)`
- `ToolResult.error(String errorMessage)` / `ToolResult.error(String errorMessage, Map<String,Object> metadata)`
- `toToolMessage(String toolCallId)` — 转换为 `ToolMessage`，作为 Observation 传回 LLM

### ToolExecutionContext (domain.tool.ToolExecutionContext)

Builder 模式构造，封装：
- `workspace: Path` — 工作目录根路径
- `timeout: Duration` — 默认 120s
- `requireShellApproval: boolean` — 默认 true
- `requireWriteApproval: boolean` — 默认 true
- `approvalHandler: ApprovalHandler` — 审批策略
- `workspaceGuard: WorkspaceGuard` — 路径安全校验

### ToolRegistry Interface (domain.tool.ToolRegistry)

```java
public interface ToolRegistry {
    void register(Tool tool);
    Optional<Tool> find(String name);
    List<ToolDefinition> definitions();
    ToolResult execute(ToolCall call, ToolExecutionContext context);
}
```

### ApprovalHandler Interface (domain.tool.ApprovalHandler)

```java
@FunctionalInterface
public interface ApprovalHandler {
    boolean approve(ApprovalRequest request);
}
```

策略模式，支持 `AlwaysApproveHandler`（测试用）和 `ConsoleApprovalHandler`（CLI 交互）。

## Core Components

### WorkspaceGuard — Path Security

安全策略：
1. 输入路径 normalize 后 `startsWith(workspaceRoot)`
2. 禁止 `../` 路径逃逸
3. 禁止绝对路径越权（resolve 后 workspace-prefixed）
4. 符号链接解析 (`toRealPath()`) 后二次校验

```java
public Path resolveAndValidate(String relativePath) throws SecurityException {
    Path resolved = workspaceRoot.resolve(relativePath).normalize();
    validate(resolved); // checks startsWith(workspaceRoot)
    // Symlink re-check
    if (Files.isSymbolicLink(resolved)) {
        Path realPath = resolved.toRealPath();
        if (!realPath.startsWith(workspaceRoot)) throw new SecurityException(...);
    }
    return resolved;
}
```

### CommandSafetyChecker — Dangerous Command Detection

基于正则模式匹配的危险命令黑名单：

| 检测命令 | 正则模式 |
|----------|----------|
| `rm -rf /` | `(?i)\brm\s+-rf\s+/\b` |
| `git reset --hard` | `(?i)\bgit\s+reset\s+--hard\b` |
| `shutdown` / `reboot` | `(?i)\bshutdown\b` |
| `dd if=` | `(?i)\bdd\s+if=` |
| `mkfs` | `(?i)\bmkfs\b` |
| ... | ... (共14+ 模式) |

```java
public void check(String command) { // 抛出 SecurityException
    String detected = detectDanger(command);
    if (detected != null) throw new SecurityException("Dangerous command: " + detected);
}
```

### ToolRegistryImpl

线程安全的 `ConcurrentHashMap` 存储，双层安全网：
1. `Tool.execute()` 内部 try-catch
2. `ToolRegistryImpl.execute()` 兜底捕获并标记 `security_violation`

### ToolArgumentParser

不动 `ToolCall` 现有结构，在执行层提供工厂方法：
```java
public static Map<String, Object> parseArguments(ToolCall call) {
    // Jackson readValue args JSON string -> Map
}
```

## Five Tool Designs

### 1. read_file

| Parameter | Type | Required | Default |
|-----------|------|----------|---------|
| `path` | string | yes | — |
| `maxChars` | integer | no | 20000 |

**Flow:** parseArgs → WorkspaceGuard.resolveAndValidate → Files.exists/isRegularFile check → Files.readString(UTF_8) → TextTruncator.truncate → success/error

**Edge cases:** 大文件截断(truncated flag)、二进制文件返回错误、空文件正常返回、路径为空返回错误

### 2. write_file

| Parameter | Type | Required | Default |
|-----------|------|----------|---------|
| `path` | string | yes | — |
| `content` | string | yes | — |
| `overwrite` | boolean | no | false |

**Flow:** parseArgs → WorkspaceGuard → exists+overwrite check → ApprovalHandler.approve → Files.createDirectories(parent) → Files.writeString(UTF_8)

**Key:** overwrite=false 默认值 + 审批双重保护

### 3. list_files

| Parameter | Type | Required | Default |
|-----------|------|----------|---------|
| `path` | string | no | `.` |
| `maxDepth` | integer | no | 3 |
| `includeHidden` | boolean | no | false |

**Default ignored dirs:** `.git`, `target`, `node_modules`, `.idea`, `__pycache__`

**Flow:** parseArgs → WorkspaceGuard → Files.isDirectory check → Files.walkFileTree（maxDepth 限制）→ 树状格式化 → TextTruncator(30000) → success

**Output:** tree-like format with `├──` / `└──` connectors

### 4. run_shell

| Parameter | Type | Required | Default |
|-----------|------|----------|---------|
| `command` | string | yes | — |
| `timeoutSeconds` | integer | no | 120 |

**Flow:** parseArgs → CommandSafetyChecker.check → ApprovalHandler.approve → ProcessBuilder (cmd /c or sh -c) → Process.waitFor(timeout) → stdout/stderr capture → TextTruncator → ToolResult with {exitCode, durationMs, truncated}

**ProcessBuilder:** Runtime OS detection, `pb.directory(workspace)`, `redirectErrorStream(false)`, timeout → `process.destroyForcibly()`

### 5. create_project_structure

| Parameter | Type | Required | Default |
|-----------|------|----------|---------|
| `projectType` | string | yes | — |
| `basePath` | string | no | `.` |
| `groupId` | string | no | `com.example` |
| `artifactId` | string | no | `demo` |
| `packageName` | string | no | groupId-derived |
| `overwrite` | boolean | no | false |

**Phase 1:** Only `maven-java` project type

**Template files:** `pom.xml` (Java 17), `App.java`, `AppTest.java` (JUnit 5), `README.md`

**Flow:** parseArgs → validate projectType → WorkspaceGuard → collect file list → ApprovalHandler.approve → iterate create (mkdir -p, check overwrite, write) → summary result

## Integration Contract with ReAct Loop

### ToolResult → Observation

```
ToolResult.success → ToolResult.content → ToolMessage.content
ToolResult.error   → "Error: " + ToolResult.error → ToolMessage.content
```

### ToolDefinition → DeepSeek Tools

```
registry.definitions() → List<ToolDefinition> → ChatRequest.builder().tools(list)
```

`ToolDefinition` 已通过 Jackson 序列化为 `{"type":"function","function":{...}}` 格式，与 DeepSeek API 兼容。

### Execution Loop (for future ReAct)

```java
for (ToolCall tc : assistantMessage.getToolCalls()) {
    ToolResult result = toolRegistry.execute(tc, context);
    messages.add(result.toToolMessage(tc.getId()));
}
// Continue loop with chatModel.chat(request.withMessages(messages))
```

## File Change Summary

### New Files (21 total)

**Domain layer (8):** `Tool.java`, `ToolResult.java`, `ToolRegistry.java`, `ToolExecutionContext.java`, `WorkspaceGuard.java`, `CommandSafetyChecker.java`, `ApprovalHandler.java`, `ApprovalRequest.java`

**Common (2):** `SecurityException.java`, `ToolExecutionException.java`

**Infrastructure (11):** `ToolRegistryImpl.java`, `ToolArgumentParser.java`, `TextTruncator.java`, `AlwaysApproveHandler.java`, `ConsoleApprovalHandler.java`, `ReadFileTool.java`, `WriteFileTool.java`, `ListFilesTool.java`, `RunShellTool.java`, `CreateProjectStructureTool.java`

### Modified Files (1-2)

- `Main.java` — tool registration and context assembly
- `AgentAppService.java` — optional: inject ToolRegistry

### Unchanged Files

- `domain/tool/ToolDefinition.java`, `ToolParameter.java`, `JsonSchemaProperty.java`, `ToolCall.java`
- `domain/chat/ChatRequest.java`, `ChatResponse.java`
- `domain/message/ToolMessage.java`
- `infrastructure/deepseek/*`
