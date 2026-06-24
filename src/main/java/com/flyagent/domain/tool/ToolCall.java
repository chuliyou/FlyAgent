package com.flyagent.domain.tool;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 工具调用意图。
 *
 * <p>反序列化自 DeepSeek API 响应中的 tool_calls 数组。
 * arguments 保持为 JSON 字符串透传，由上层按需解析。</p>
 *
 * @author FlyAgent Team
 * @since 1.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ToolCall {

    private final String id;
    private final String type;
    private final FunctionCall function;

    @JsonCreator
    public ToolCall(
            @JsonProperty("id") String id,
            @JsonProperty("type") String type,
            @JsonProperty("function") FunctionCall function) {
        this.id = id;
        this.type = type;
        this.function = function;
    }

    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public FunctionCall getFunction() {
        return function;
    }

    /**
     * 函数调用详情内部类。
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FunctionCall {

        private final String name;
        private final String arguments;

        @JsonCreator
        public FunctionCall(
                @JsonProperty("name") String name,
                @JsonProperty("arguments") String arguments) {
            this.name = name;
            this.arguments = arguments;
        }

        public String getName() {
            return name;
        }

        public String getArguments() {
            return arguments;
        }
    }
}
