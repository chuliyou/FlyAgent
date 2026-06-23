# Proposal: Add Tool Capabilities

## Summary

为 FlyAgent 新增 5 个基础本地工具（`read_file`、`write_file`、`list_files`、`run_shell`、`create_project_structure`），以及支撑工具层的统一接口 `Tool`、注册表 `ToolRegistry`、执行上下文 `ToolExecutionContext`、路径安全校验 `WorkspaceGuard`、命令安全检测 `CommandSafetyChecker`、审批处理器 `ApprovalHandler` 等核心领域模型和基础设施组件。

## Why

### 当前状态

M1（工程初始化与 DDD 骨架）+ M3（DeepSeek 模型调用）完成后，FlyAgent 已具备：

- 项目骨架和分层架构
- DeepSeek API 客户端与消息格式
- `ToolDefinition`、`ToolCall`、`ToolParameter`、`JsonSchemaProperty` 等工具定义模型
- 完整的 `chat` 方法

**核心痛点：** 工具定义模型已就绪，但**工具未实现**。Agent 无法执行任何本地动作（读文件、写文件、执行 Shell 命令等），缺少 ReAct 循环的基础执行层。

### 为什么现在做

1. **最小可行闭环：** 工具层是 ReAct 模式中 "Action → Observation" 的核心环节，先实现工具层可独立验收，后续 ReAct Loop 可直接对接
2. **架构完整性：** 当前 `domain/tool/` 包仅有数据模型，缺少行为接口和实现，补齐后形成完整的六边形架构工具端口/适配器
3. **独立可测：** 5 个工具 + 安全组件可独立开发和测试，不依赖 ReAct Loop 或 DeepSeek 客户端

## What

### 交付成果

1. **领域模型（10 个文件）**
   - `Tool` 接口 — 工具统一端口
   - `ToolResult` — 执行结果值对象
   - `ToolRegistry` 接口 — 工具注册表端口
   - `ToolExecutionContext` — 执行上下文值对象
   - `WorkspaceGuard` — 路径安全校验领域服务
   - `CommandSafetyChecker` — 命令安全检测领域服务
   - `ApprovalHandler` 接口 + `ApprovalRequest` — 审批处理器端口
   - `SecurityException` + `ToolExecutionException` — 新增异常类型

2. **基础设施实现（9 个文件）**
   - `ToolRegistryImpl` — 注册表实现
   - `ToolArgumentParser` — 参数解析工具
   - `TextTruncator` — 文本截断工具
   - `AlwaysApproveHandler` + `ConsoleApprovalHandler` — 审批实现
   - 5 个工具实现：`ReadFileTool`、`WriteFileTool`、`ListFilesTool`、`RunShellTool`、`CreateProjectStructureTool`

3. **测试（12 个测试类，预计 70+ 测试用例）**

4. **集成变更**
   - `Main.java` 中注册 5 个工具，构造 ToolExecutionContext

### 非目标

- 完整 ReAct Loop（M4+）
- 多轮自动执行
- 持久化
- 流式响应
- patch 工具、沙箱、插件化加载

## Approach

遵循项目现有的**六边形架构 + DDD 分层**风格：

- **Domain 层**：定义 `Tool` 接口（端口）、`ToolRegistry` 接口（端口）、`ToolResult`、`ToolExecutionContext`、`WorkspaceGuard`、`CommandSafetyChecker`、`ApprovalHandler` 接口
- **Infrastructure 层**：提供 5 个工具实现和 `ToolRegistryImpl`、审批处理器实现等适配器
- **应用层/接口层**：本次改动最小（仅在 Main.java 中添加依赖组装）

### 设计原则

- 工具实现不依赖任何 infrastructure 具体实现（仅依赖 domain 接口）
- `Tool.execute()` 捕获所有异常转为 `ToolResult.error()`，不抛出到调用方
- `ToolResult.toToolMessage(toolCallId)` 作为与 LLM 的 Observation 桥梁
- `WorkspaceGuard` 作为所有文件操作的安全边界，在每个文件工具中强制执行
- `ApprovalHandler` 作为可替换策略，write/shell 操作默认需要审批

## Dependencies

- 依赖 M1 的工程骨架和 DDD 分层
- 依赖现有 `domain/tool/` 下的 `ToolDefinition`、`ToolCall`、`ToolParameter`、`JsonSchemaProperty`（不变更）
- 依赖现有 `domain/message/ToolMessage`
- 不依赖任何第三方 Agent 框架

## Risks

| 风险 | 缓解 |
|------|------|
| 路径越权（WorkspaceGuard 被绕过） | 所有工具路径操作统一经过 `resolveAndValidate()`，符号链接二次校验 |
| Shell 命令破坏用户环境 | `CommandSafetyChecker` 黑名单 + `ApprovalHandler` 审批双重防护 |
| 工具输出过长挤占 LLM 上下文 | `TextTruncator` 统一截断，metadata 标记 truncated |
| ProcessBuilder 跨平台兼容性 | 运行时检测 OS 类型选择 `cmd /c` 或 `sh -c` |

## References

- [PRD: 第二次开发 PRD -- 基础工具能力](../prd/第二次开发PRD-基础工具能力.md)
- [技术方案: 第二次开发 -- 基础工具能力 技术方案](../技术方案/第二次开发-基础工具能力-技术方案.md)
- [系统架构: project.md](../project.md)
