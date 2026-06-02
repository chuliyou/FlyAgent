package com.flyagent.domain.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 工具执行结果消息。
 *
 * <p>工具执行完成后回传给模型，必须携带 tool_call_id 以关联原始工具调用。</p>
 *
 * @author FlyAgent Team
 * @since 1.0
 */
public class ToolMessage implements Message {

    private final String toolCallId;
    private final String content;

    @JsonCreator
    public ToolMessage(
            @JsonProperty("tool_call_id") String toolCallId,
            @JsonProperty("content") String content) {
        this.toolCallId = toolCallId;
        this.content = content;
    }

    @Override
    public MessageRole getRole() {
        return MessageRole.TOOL;
    }

    @JsonProperty("tool_call_id")
    public String getToolCallId() {
        return toolCallId;
    }

    public String getContent() {
        return content;
    }
}
