package com.flyagent.domain.tool;

import java.util.List;
import java.util.Map;

/**
 * 工具定义。
 *
 * <p>表示一个可用工具的完整描述，遵循 DeepSeek function tool calling 协议。
 * 由名称、用途说明和 JSON Schema 参数组成。</p>
 *
 * @author FlyAgent Team
 * @since 1.0
 */
public class ToolDefinition {

    private final String type = "function";
    private final FunctionDefinition function;

    public ToolDefinition(String name, String description, ToolParameter parameters) {
        this.function = new FunctionDefinition(name, description, parameters);
    }

    public String getType() {
        return type;
    }

    public FunctionDefinition getFunction() {
        return function;
    }

    /**
     * 工具函数定义内部类。
     */
    public static class FunctionDefinition {

        private final String name;
        private final String description;
        private final ToolParameter parameters;

        public FunctionDefinition(String name, String description, ToolParameter parameters) {
            this.name = name;
            this.description = description;
            this.parameters = parameters;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        public ToolParameter getParameters() {
            return parameters;
        }
    }
}
