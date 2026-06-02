package com.flyagent.application.service;

import com.flyagent.application.dto.AgentResponseDTO;
import com.flyagent.domain.chat.ChatChoice;
import com.flyagent.domain.chat.ChatModelPort;
import com.flyagent.domain.chat.ChatRequest;
import com.flyagent.domain.chat.ChatResponse;
import com.flyagent.domain.message.AssistantMessage;
import com.flyagent.domain.message.UserMessage;
import com.flyagent.domain.session.AgentSession;

import java.util.List;

/**
 * Agent 应用服务。
 *
 * <p>负责编排用户输入处理流程：组装请求 → 调用模型 → 转换 DTO。</p>
 *
 * @author FlyAgent Team
 * @since 1.0
 */
public class AgentAppService {

    private final ChatModelPort chatModel;
    private final SessionAppService sessionService;

    public AgentAppService(ChatModelPort chatModel, SessionAppService sessionService) {
        this.chatModel = chatModel;
        this.sessionService = sessionService;
    }

    /**
     * 处理用户输入。
     *
     * @param userInput 用户输入的自然语言文本
     * @return Agent 响应 DTO
     */
    public AgentResponseDTO handleUserInput(String userInput) {
        if (!sessionService.hasActiveSession()) {
            return AgentResponseDTO.error("No active session. Please start a session first.");
        }

        AgentSession session = sessionService.getCurrentSession();

        // 构造消息列表（一期：单轮对话，不携带历史）
        UserMessage userMessage = new UserMessage(userInput);
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(userMessage))
                .build();

        // 调用模型
        ChatResponse response = chatModel.chat(request);
        ChatChoice firstChoice = response.firstChoice();

        if (firstChoice == null) {
            return AgentResponseDTO.error("No response from model");
        }

        // 提取助手消息
        AssistantMessage assistantMsg = firstChoice.getMessage();
        String content = assistantMsg.hasContent() ? assistantMsg.getContent() : "";

        return AgentResponseDTO.success(
                content,
                assistantMsg.hasToolCalls(),
                response.getUsage()
        );
    }
}
