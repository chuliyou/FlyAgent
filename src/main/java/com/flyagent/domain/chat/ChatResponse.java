package com.flyagent.domain.chat;

import java.util.List;

/**
 * 聊天响应领域值对象。
 *
 * <p>封装一次完整的 API 响应，包含请求标识、模型名、选择结果和 token 用量。</p>
 *
 * @author FlyAgent Team
 * @since 1.0
 */
public class ChatResponse {

    private final String id;
    private final String model;
    private final List<ChatChoice> choices;
    private final TokenUsage usage;

    public ChatResponse(String id, String model, List<ChatChoice> choices, TokenUsage usage) {
        this.id = id;
        this.model = model;
        this.choices = choices;
        this.usage = usage;
    }

    public String getId() {
        return id;
    }

    public String getModel() {
        return model;
    }

    public List<ChatChoice> getChoices() {
        return choices;
    }

    public TokenUsage getUsage() {
        return usage;
    }

    /**
     * 获取第一个选择结果。
     *
     * @return 第一个 ChatChoice，若无则返回 null
     */
    public ChatChoice firstChoice() {
        if (choices != null && !choices.isEmpty()) {
            return choices.get(0);
        }
        return null;
    }
}
