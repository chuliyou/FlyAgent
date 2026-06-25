---
name: flyagent-agent-reAct-loop-tech-solution
description: FlyAgent 第三次开发 Agent 与 ReAct 循环技术方案，基于 M1+M3 代码基线和第二次开发工具层
metadata:
  type: project
---

技术方案已产出并写入 `J:\code\FlyAgent\技术方案\第三次开发-Agent与ReAct循环-技术方案.md`。

**Why:** 第三次开发需要将 LLM 调用能力（ChatModelPort）和工具执行能力（ToolRegistry）串联起来，实现 Agent 执行核心，通过 ReAct 循环协调 DeepSeek 模型调用和本地工具执行。

**关键设计决策：**
- **复用 ChatModelPort** 而非新建 CodingChatModel 端口：现有端口已支持 tools、messages、thinking 等全部能力
- **Agent 接口作为领域端口**：DefaultAgent 实现，允许未来替换执行策略（ReAct / Plan-and-Execute）
- **Conversation 领域聚合**：内存存储，窗口 20 条消息，工具结果截断 4000 字符
- **ReActLoop 领域服务**：驱动 AgentTask 状态机，协调 LLM 调用和工具执行
- **ContextBuildService 领域服务**：组装 System Prompt + 工具定义 + 历史消息
- **ReActStep 值对象**：不可变 + Builder 模式，记录 Thought/Action/Observation
- **不引入第三方 Agent 框架**（LangChain, AutoGPT, Semantic Kernel 等）
- 5 个里程碑：M1 基础模型 -> M2 上下文构建 -> M3 ReActLoop 核心 -> M4 Agent 集成 -> M5 测试与验收
