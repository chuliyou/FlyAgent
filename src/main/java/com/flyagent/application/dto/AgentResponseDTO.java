package com.flyagent.application.dto;

import com.flyagent.domain.chat.TokenUsage;

/**
 * Agent 响应 DTO。
 *
 * <p>封装应用层返回给 CLI 层的响应数据，屏蔽领域模型细节。</p>
 *
 * @author FlyAgent Team
 * @since 1.0
 */
public class AgentResponseDTO {

    /** 是否成功 */
    private final boolean success;

    /** 文本内容 */
    private final String content;

    /** 是否包含工具调用 */
    private final boolean hasToolCalls;

    /** Token 用量 */
    private final TokenUsage tokenUsage;

    /** 错误信息 */
    private final String errorMessage;

    private AgentResponseDTO(boolean success, String content, boolean hasToolCalls,
                              TokenUsage tokenUsage, String errorMessage) {
        this.success = success;
        this.content = content;
        this.hasToolCalls = hasToolCalls;
        this.tokenUsage = tokenUsage;
        this.errorMessage = errorMessage;
    }

    /**
     * 构造成功响应。
     *
     * @param content 文本内容
     * @param hasToolCalls 是否包含工具调用
     * @param usage Token 用量
     * @return AgentResponseDTO
     */
    public static AgentResponseDTO success(String content, boolean hasToolCalls,
                                            TokenUsage usage) {
        return new AgentResponseDTO(true, content, hasToolCalls, usage, null);
    }

    /**
     * 构造错误响应。
     *
     * @param errorMessage 错误信息
     * @return AgentResponseDTO
     */
    public static AgentResponseDTO error(String errorMessage) {
        return new AgentResponseDTO(false, null, false, null, errorMessage);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getContent() {
        return content;
    }

    public boolean isHasToolCalls() {
        return hasToolCalls;
    }

    public TokenUsage getTokenUsage() {
        return tokenUsage;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
