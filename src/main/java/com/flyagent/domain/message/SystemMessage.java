package com.flyagent.domain.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 系统提示词消息。
 *
 * <p>用于设定 Agent 的基础行为约束。</p>
 *
 * @author FlyAgent Team
 * @since 1.0
 */
public class SystemMessage implements Message {

    private final String content;

    @JsonCreator
    public SystemMessage(@JsonProperty("content") String content) {
        this.content = content;
    }

    @Override
    public MessageRole getRole() {
        return MessageRole.SYSTEM;
    }

    public String getContent() {
        return content;
    }
}
