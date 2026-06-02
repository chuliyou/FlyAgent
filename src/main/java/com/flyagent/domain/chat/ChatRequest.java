package com.flyagent.domain.chat;

import com.flyagent.common.constant.CommonConstants;
import com.flyagent.domain.message.Message;
import com.flyagent.domain.tool.ToolDefinition;

import java.util.List;

/**
 * 聊天请求领域值对象。
 *
 * <p>采用 Builder 模式构造，提供合理的 DeepSeek 默认参数。</p>
 *
 * @author FlyAgent Team
 * @since 1.0
 */
public class ChatRequest {

    private final String model;
    private final List<Message> messages;
    private final List<ToolDefinition> tools;
    private final boolean thinking;
    private final String reasoningEffort;
    private final boolean stream;

    private ChatRequest(Builder builder) {
        this.model = builder.model;
        this.messages = builder.messages;
        this.tools = builder.tools;
        this.thinking = builder.thinking;
        this.reasoningEffort = builder.reasoningEffort;
        this.stream = builder.stream;
    }

    public String getModel() {
        return model;
    }

    public List<Message> getMessages() {
        return messages;
    }

    public List<ToolDefinition> getTools() {
        return tools;
    }

    public boolean isThinking() {
        return thinking;
    }

    public String getReasoningEffort() {
        return reasoningEffort;
    }

    public boolean isStream() {
        return stream;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * ChatRequest 建造器。
     */
    public static class Builder {

        private String model = CommonConstants.DEFAULT_MODEL;
        private List<Message> messages;
        private List<ToolDefinition> tools;
        private boolean thinking = true;
        private String reasoningEffort = "high";
        private boolean stream = false;

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder messages(List<Message> messages) {
            this.messages = messages;
            return this;
        }

        public Builder tools(List<ToolDefinition> tools) {
            this.tools = tools;
            return this;
        }

        public Builder thinking(boolean thinking) {
            this.thinking = thinking;
            return this;
        }

        public Builder reasoningEffort(String reasoningEffort) {
            this.reasoningEffort = reasoningEffort;
            return this;
        }

        public Builder stream(boolean stream) {
            this.stream = stream;
            return this;
        }

        /**
         * 构建 ChatRequest。
         *
         * @return ChatRequest 实例
         * @throws IllegalArgumentException 当 messages 为空时
         */
        public ChatRequest build() {
            if (messages == null || messages.isEmpty()) {
                throw new IllegalArgumentException("messages must not be empty");
            }
            return new ChatRequest(this);
        }
    }
}
