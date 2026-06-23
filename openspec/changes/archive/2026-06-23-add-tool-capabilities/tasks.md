# Tasks: Add Tool Capabilities

## M1: Domain Model & Registry (Tools Foundation)

- [x] 1.1 Create `Tool` interface at `domain/tool/Tool.java`
- [x] 1.2 Create `ToolResult` value object at `domain/tool/ToolResult.java`
- [x] 1.3 Create `ToolExecutionContext` value object at `domain/tool/ToolExecutionContext.java`
- [x] 1.4 Create `ToolRegistry` interface at `domain/tool/ToolRegistry.java`
- [x] 1.5 Create `ApprovalHandler` interface and `ApprovalRequest` value object at `domain/tool/`
- [x] 1.6 Create new exception types at `common/exception/`
- [x] 1.7 Create `ToolRegistryImpl` at `infrastructure/tools/ToolRegistryImpl.java`
- [x] 1.8 Create `ToolArgumentParser` utility at `infrastructure/tools/ToolArgumentParser.java`
- [x] 1.9 Write unit tests for M1

## M2: Security & Approval

- [x] 2.1 Implement `WorkspaceGuard` at `domain/tool/WorkspaceGuard.java`
- [x] 2.2 Implement `CommandSafetyChecker` at `domain/tool/CommandSafetyChecker.java`
- [x] 2.3 Implement `TextTruncator` utility at `infrastructure/tools/TextTruncator.java`
- [x] 2.4 Implement `AlwaysApproveHandler` at `infrastructure/tools/AlwaysApproveHandler.java`
- [x] 2.5 Implement `ConsoleApprovalHandler` at `infrastructure/tools/ConsoleApprovalHandler.java`
- [x] 2.6 Write unit tests for M2

## M3: File & Directory Tools

- [x] 3.1 Implement `ReadFileTool` at `infrastructure/tools/ReadFileTool.java`
- [x] 3.2 Implement `WriteFileTool` at `infrastructure/tools/WriteFileTool.java`
- [x] 3.3 Implement `ListFilesTool` at `infrastructure/tools/ListFilesTool.java`
- [x] 3.4 Write unit tests for M3
  - `ReadFileToolTest`: 6+ cases (existing file, missing file, directory, path traversal, large file truncation, empty file)
  - `WriteFileToolTest`: 6+ cases (new file, auto-create parent dirs, overwrite=false conflict, overwrite=true success, approval denied, path traversal)
  - `ListFilesToolTest`: 6+ cases (root listing, subdirectory, maxDepth limit, default ignores, includeHidden, path traversal)
  - Use `@TempDir` for isolated workspaces, mock `ApprovalHandler`

## M4: Shell & Project Structure Tools

- [x] 4.1 Implement `RunShellTool` at `infrastructure/tools/RunShellTool.java`
- [x] 4.2 Implement `CreateProjectStructureTool` at `infrastructure/tools/CreateProjectStructureTool.java`
- [x] 4.3 Write unit tests for M4
  - `RunShellToolTest`: 6+ cases (echo success, error command exit code, approval denied, dangerous command blocked, timeout, stdout/stderr separation)
  - `CreateProjectStructureToolTest`: 6+ cases (maven-java creation, existing files not overwritten, overwrite=true, invalid projectType, path traversal, partial failure summary)

## M5: Integration & Verification

- [x] 5.1 Update `Main.java` to register all 5 tools, construct `ToolExecutionContext`, inject `ToolRegistry`
- [x] 5.2 Verify DeepSeek function tool definition JSON Schema output
- [x] 5.3 Conduct manual end-to-end test with tool calls
- [x] 5.4 Run regression tests to ensure existing functionality unaffected

## Task Summary

| Phase | Tasks | Files Created | Test Files |
|-------|-------|--------------|------------|
| M1 | 9 tasks | 10 files | 3 test files |
| M2 | 6 tasks | 5 files | 3 test files |
| M3 | 4 tasks | 3 files | 3 test files |
| M4 | 3 tasks | 2 files | 2 test files |
| M5 | 4 tasks | 1 modified (Main.java) | 0 |
| **Total** | **26 tasks** | **21 new + 1-2 modified** | **11 test files** |
