package com.flyagent.domain.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 用户输入消息。
 *
 * <p>一期仅支持纯文本内容。</p>
 *
 * @author FlyAgent Team
 * @since 1.0
 */
public class UserMessage implements Message {

    private final String content;

    @JsonCreator
    public UserMessage(@JsonProperty("content") String content) {
        this.content = content;
    }

    @Override
    public MessageRole getRole() {
        return MessageRole.USER;
    }

    public String getContent() {
        return content;
    }
}
