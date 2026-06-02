package com.flyagent.domain.message;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 消息角色枚举。
 *
 * <p>对应 DeepSeek Chat Completions API 的消息角色体系。</p>
 *
 * @author FlyAgent Team
 * @since 1.0
 */
public enum MessageRole {

    /** 系统提示词 */
    SYSTEM("system"),

    /** 用户输入 */
    USER("user"),

    /** 模型回复 */
    ASSISTANT("assistant"),

    /** 工具执行结果回传 */
    TOOL("tool");

    private final String value;

    MessageRole(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
