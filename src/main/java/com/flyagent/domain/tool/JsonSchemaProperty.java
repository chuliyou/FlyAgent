package com.flyagent.domain.tool;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * JSON Schema 属性描述。
 *
 * <p>单个参数的属性定义，包含类型、描述和可选的枚举值列表。</p>
 *
 * @author FlyAgent Team
 * @since 1.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JsonSchemaProperty {

    private final String type;
    private final String description;
    private final List<String> enumValues;

    public JsonSchemaProperty(String type, String description) {
        this(type, description, null);
    }

    public JsonSchemaProperty(String type, String description, List<String> enumValues) {
        this.type = type;
        this.description = description;
        this.enumValues = enumValues;
    }

    public String getType() {
        return type;
    }

    public String getDescription() {
        return description;
    }

    public List<String> getEnum() {
        return enumValues;
    }
}
