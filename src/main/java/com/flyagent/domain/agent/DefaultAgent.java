package com.flyagent.domain.agent;

import com.flyagent.application.service.SessionAppService;
import com.flyagent.domain.chat.ChatModelPort;
import com.flyagent.domain.session.AgentSession;
import com.flyagent.domain.session.SessionId;
import com.flyagent.domain.task.AgentTask;
import com.flyagent.domain.tool.ApprovalHandler;
import com.flyagent.domain.tool.ToolExecutionContext;
import com.flyagent.domain.tool.ToolRegistry;
import com.flyagent.domain.tool.WorkspaceGuard;

/**
 * Agent 端口默认实现。
 *
 * <p>采用 ReAct 循环策略：组装 AgentTask、Conversation 和 ToolExecutionContext，
 * 委托 ReActLoop 执行完整的 Thought → Action → Observation 迭代。</p>
 *
 * <p>构造器注入所有依赖端口，不直接依赖任何基础设施实现。</p>
 *
 * @author FlyAgent Team
 * @since 1.0
 */
public class DefaultAgent implements Agent {

    private static final int DEFAULT_MAX_TURNS = 8;

    private final ChatModelPort chatModel;
    private final ToolRegistry toolRegistry;
    private final SessionAppService sessionService;
    private final ApprovalHandler approvalHandler;

    /**
     * 构造 DefaultAgent。
     *
     * @param chatModel       LLM 调用端口
     * @param toolRegistry    工具注册表端口
     * @param sessionService  会话服务
     * @param approvalHandler 审批处理器
     */
    public DefaultAgent(ChatModelPort chatModel, ToolRegistry toolRegistry,
                        SessionAppService sessionService, ApprovalHandler approvalHandler) {
        this.chatModel = chatModel;
        this.toolRegistry = toolRegistry;
        this.sessionService = sessionService;
        this.approvalHandler = approvalHandler;
    }

    @Override
    public AgentResponse run(AgentRequest request) {
        // 1. 校验输入（AgentRequest 在 build 时已校验，此处为防御性检查）
        if (request.getUserInput() == null || request.getUserInput().isBlank()) {
            throw new IllegalArgumentException("userInput must not be empty");
        }

        // 2. 获取或创建会话
            SessionId sessionId;
        if (request.getSessionId() != null) {
            sessionId = SessionId.of(request.getSessionId());
        } else {
            if (sessionService.hasActiveSession()) {
                sessionId = sessionService.getCurrentSession().getId();
            } else {
                // 无活跃会话时启动新会话
                AgentSession session = sessionService.startSession(
                        request.getWorkspace().toString());
                sessionId = session.getId();
            }
        }

        // 3. 创建任务
        int maxTurns = request.getMaxTurns() > 0 ? request.getMaxTurns() : DEFAULT_MAX_TURNS;
        AgentTask task = new AgentTask(sessionId, request.getUserInput(), maxTurns);

        // 4. 获取或创建 Conversation（session 级别生命周期）
        AgentSession currentSession = sessionService.getCurrentSession();
        Conversation conversation;
        if (currentSession != null && currentSession.getConversation() != null) {
            conversation = currentSession.getConversation();
        } else {
            conversation = new Conversation();
        }
        conversation.addUserMessage(request.getUserInput());

        // 5. 创建上下文构建服务
        ContextBuildService contextBuilder = new ContextBuildService();

        // 6. 构建工具执行上下文
        ToolExecutionContext toolContext = ToolExecutionContext
                .builder(request.getWorkspace())
                .approvalHandler(approvalHandler)
                .workspaceGuard(new WorkspaceGuard(request.getWorkspace()))
                .build();

        // 7. 创建 ReActLoop 并委托执行
        ReActLoop loop = new ReActLoop(chatModel, toolRegistry);
        return loop.execute(request, toolContext, contextBuilder, conversation, task);
    }
}
