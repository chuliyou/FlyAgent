---
name: flyagent-tool-layer-tech-solution
description: FlyAgent 第二次开发基础工具能力技术方案，面向已完成的 M1+M3 代码基线设计
metadata:
  type: project
---

技术方案已产出并写入 `J:\code\FlyAgent\技术方案\第二次开发-基础工具能力-技术方案.md`。

**Why:** 第二次开发需要在现有六边形架构 + DDD 分层代码基线上实现 5 个基础工具（read_file, write_file, list_files, run_shell, create_project_structure），为后续 ReAct Loop 做准备。

**关键设计决策：**
- Tool 接口定义为领域端口（`domain/tool/Tool.java`），实现在 `infrastructure/tools/`
- 复用现有 `ToolDefinition` / `ToolParameter` / `JsonSchemaProperty` / `ToolCall`（API 层不变，执行层通过 `ToolArgumentParser` 适配）
- WorkspaceGuard / CommandSafetyChecker 为领域服务（纯逻辑，无基础设施依赖）
- ApprovalHandler 为领域端口（可替换审批策略）
- Main.java 继续 Pure DI 模式组装依赖
- 5 个里程碑：M1 基础模型 -> M2 安全与审批 -> M3 文件与目录 -> M4 Shell 与项目结构 -> M5 集成与验收
