package com.flyagent.application.service;

import com.flyagent.application.dto.AgentResponseDTO;
import com.flyagent.domain.agent.Agent;
import com.flyagent.domain.agent.AgentRequest;
import com.flyagent.domain.agent.AgentResponse;
import com.flyagent.domain.session.AgentSession;

/**
 * Agent 应用服务。
 *
 * <p>负责编排用户输入处理流程：构建 AgentRequest → 委托 Agent 执行 → 转换 DTO。
 * Agent 端口支持多轮 ReAct 循环，替代原有的单轮 ChatModelPort 直接调用。</p>
 *
 * @author FlyAgent Team
 * @since 1.0
 */
public class AgentAppService {

    private final Agent agent;
    private final SessionAppService sessionService;

    public AgentAppService(Agent agent, SessionAppService sessionService) {
        this.agent = agent;
        this.sessionService = sessionService;
    }

    /**
     * 处理用户输入（ReAct 多轮对话）。
     *
     * @param userInput 用户输入的自然语言文本
     * @return Agent 响应 DTO
     */
    public AgentResponseDTO handleUserInput(String userInput) {
        if (!sessionService.hasActiveSession()) {
            return AgentResponseDTO.error("No active session. Please start a session first.");
        }

        AgentSession session = sessionService.getCurrentSession();

        // 构建 AgentRequest
        AgentRequest request = AgentRequest.builder()
                .sessionId(session.getId().getValue())
                .userInput(userInput)
                .workspace(session.getWorkspace().getPath())
                .build();

        // 委托 Agent 执行
        AgentResponse agentResponse = agent.run(request);

        // 转换 DTO
        return AgentResponseDTO.from(agentResponse);
    }
}
