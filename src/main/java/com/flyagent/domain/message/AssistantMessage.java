package com.flyagent.domain.message;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.flyagent.domain.tool.ToolCall;

import java.util.List;

/**
 * 模型回复消息。
 *
 * <p>可能包含纯文本回复，也可能包含工具调用请求。通过工厂方法区分两种构造意图。</p>
 *
 * @author FlyAgent Team
 * @since 1.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AssistantMessage implements Message {

    private final String content;
    private final List<ToolCall> toolCalls;

    public AssistantMessage(String content, List<ToolCall> toolCalls) {
        this.content = content;
        this.toolCalls = toolCalls;
    }

    /**
     * 构造纯文本回复消息。
     *
     * @param content 文本内容
     * @return AssistantMessage
     */
    public static AssistantMessage ofText(String content) {
        return new AssistantMessage(content, null);
    }

    /**
     * 构造工具调用消息。
     *
     * @param toolCalls 工具调用列表
     * @return AssistantMessage
     */
    public static AssistantMessage ofToolCalls(List<ToolCall> toolCalls) {
        return new AssistantMessage(null, toolCalls);
    }

    @Override
    public MessageRole getRole() {
        return MessageRole.ASSISTANT;
    }

    public String getContent() {
        return content;
    }

    @JsonProperty("tool_calls")
    public List<ToolCall> getToolCalls() {
        return toolCalls;
    }

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }

    public boolean hasContent() {
        return content != null && !content.isEmpty();
    }
}
