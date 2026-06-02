package com.flyagent.domain.message;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * 消息接口。
 *
 * <p>所有消息类型的公共契约。通过 Jackson 多态注解实现按 role 字段自动路由反序列化。</p>
 *
 * @author FlyAgent Team
 * @since 1.0
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "role")
@JsonSubTypes({
        @JsonSubTypes.Type(value = SystemMessage.class, name = "system"),
        @JsonSubTypes.Type(value = UserMessage.class, name = "user"),
        @JsonSubTypes.Type(value = AssistantMessage.class, name = "assistant"),
        @JsonSubTypes.Type(value = ToolMessage.class, name = "tool")
})
public interface Message {

    /**
     * 获取消息角色。
     *
     * @return 消息角色
     */
    MessageRole getRole();
}
