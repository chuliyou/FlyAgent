package com.flyagent.domain.tool;

import com.flyagent.domain.message.ToolMessage;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 工具执行结果值对象。
 *
 * <p>封装工具执行的全部产出，支持快速成功构造和失败构造。
 * 通过 toToolMessage 方法转换为 Observation 回传模型。</p>
 *
 * @author FlyAgent Team
 * @since 1.0
 */
public class ToolResult {

    private final boolean success;
    private final String content;
    private final String error;
    private final Map<String, Object> metadata;

    private ToolResult(boolean success, String content, String error, Map<String, Object> metadata) {
        this.success = success;
        this.content = content;
        this.error = error;
        this.metadata = metadata != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(metadata))
                : Collections.emptyMap();
    }

    /** 构造成功结果 */
    public static ToolResult success(String content) {
        return new ToolResult(true, content, null, null);
    }

    /** 构造成功结果（带元数据） */
    public static ToolResult success(String content, Map<String, Object> metadata) {
        return new ToolResult(true, content, null, metadata);
    }

    /** 构造失败结果 */
    public static ToolResult error(String errorMessage) {
        return new ToolResult(false, null, errorMessage, null);
    }

    /** 构造失败结果（带元数据） */
    public static ToolResult error(String errorMessage, Map<String, Object> metadata) {
        return new ToolResult(false, null, errorMessage, metadata);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getContent() {
        return content;
    }

    public String getError() {
        return error;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    /** 是否有截断标记 */
    public boolean isTruncated() {
        return Boolean.TRUE.equals(metadata.get("truncated"));
    }

    /**
     * 转换为 ToolMessage（用于回传 DeepSeek）。
     *
     * @param toolCallId 关联的 tool_call id
     * @return ToolMessage 实例
     */
    public ToolMessage toToolMessage(String toolCallId) {
        String body;
        if (success) {
            body = content;
        } else {
            body = "Error: " + error;
        }
        return new ToolMessage(toolCallId, body);
    }
}
