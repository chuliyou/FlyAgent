package com.flyagent.domain.tool;

import java.util.List;
import java.util.Map;

/**
 * 工具参数集合。
 *
 * <p>遵循 JSON Schema object 结构，包含属性定义和必填字段列表。</p>
 *
 * @author FlyAgent Team
 * @since 1.0
 */
public class ToolParameter {

    private final String type = "object";
    private final Map<String, JsonSchemaProperty> properties;
    private final List<String> required;

    public ToolParameter(Map<String, JsonSchemaProperty> properties, List<String> required) {
        this.properties = properties != null ? properties : Map.of();
        this.required = required != null ? required : List.of();
    }

    public String getType() {
        return type;
    }

    public Map<String, JsonSchemaProperty> getProperties() {
        return properties;
    }

    public List<String> getRequired() {
        return required;
    }
}
