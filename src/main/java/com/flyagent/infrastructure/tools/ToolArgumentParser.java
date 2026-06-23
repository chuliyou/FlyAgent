package com.flyagent.infrastructure.tools;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flyagent.domain.tool.ToolCall;

import java.util.Collections;
import java.util.Map;

/**
 * ToolCall 参数解析工具。
 *
 * <p>将 ToolCall 的 JSON arguments 字符串解析为 Map，
 * 供工具实现使用。不修改现有 ToolCall 结构。</p>
 *
 * @author FlyAgent Team
 * @since 1.0
 */
public final class ToolArgumentParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ToolArgumentParser() {
    }

    /**
     * 将 ToolCall 的 JSON arguments 字符串解析为 Map。
     *
     * @param call 工具调用请求
     * @return 参数 Map，解析失败或为空时返回空 Map
     */
    public static Map<String, Object> parseArguments(ToolCall call) {
        String argsJson = call.getFunction().getArguments();
        if (argsJson == null || argsJson.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return MAPPER.readValue(argsJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }
}
