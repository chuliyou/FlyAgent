package com.flyagent.infrastructure.tools;

import com.flyagent.common.exception.SecurityException;
import com.flyagent.domain.tool.*;

import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 文件读取工具。
 *
 * <p>安全读取 workspace 内的文本文件，支持字符数截断。
 * 自动检测并拒绝二进制文件。</p>
 *
 * <h3>参数</h3>
 * <ul>
 *   <li>{@code path} — 文件路径（相对于 workspace），必填</li>
 *   <li>{@code maxChars} — 最大返回字符数，默认 20000</li>
 * </ul>
 *
 * @author FlyAgent Team
 * @since 1.0
 */
public class ReadFileTool implements Tool {

    private static final int DEFAULT_MAX_CHARS = 20000;

    @Override
    public String name() {
        return "read_file";
    }

    @Override
    public String description() {
        return "Read the contents of a text file within the workspace. "
                + "Returns the file content with line numbers. "
                + "Automatically rejects binary files.";
    }

    @Override
    public ToolDefinition definition() {
        Map<String, JsonSchemaProperty> properties = Map.of(
                "path", new JsonSchemaProperty("string", "Relative path to the file to read"),
                "maxChars", new JsonSchemaProperty("integer", "Maximum number of characters to return (default: " + DEFAULT_MAX_CHARS + ")")
        );
        ToolParameter parameters = new ToolParameter(properties, List.of("path"));
        return new ToolDefinition(name(), description(), parameters);
    }

    @Override
    public ToolResult execute(ToolCall call, ToolExecutionContext context) {
        Map<String, Object> args = ToolArgumentParser.parseArguments(call);

        // 1. Parse arguments
        String pathStr = (String) args.get("path");
        int maxChars = getIntArg(args, "maxChars", DEFAULT_MAX_CHARS);

        // 2. Validate path
        if (pathStr == null || pathStr.isBlank()) {
            return ToolResult.error("Required parameter 'path' is missing");
        }

        // 3. Resolve and validate path
        Path safePath;
        try {
            safePath = context.getWorkspaceGuard().resolveAndValidate(pathStr);
        } catch (SecurityException e) {
            return ToolResult.error("Security violation: " + e.getMessage());
        }

        // 4. Check file exists
        if (!Files.exists(safePath)) {
            return ToolResult.error("File not found: " + pathStr);
        }

        // 5. Check is regular file
        if (Files.isDirectory(safePath)) {
            return ToolResult.error("Path is a directory: " + pathStr);
        }

        // 6. Read file content
        String content;
        try {
            content = Files.readString(safePath, StandardCharsets.UTF_8);
        } catch (MalformedInputException e) {
            return ToolResult.error("Cannot read binary file: " + pathStr);
        } catch (IOException e) {
            return ToolResult.error("Failed to read file: " + pathStr + " (" + e.getMessage() + ")");
        }

        // 7. Truncate
        int totalChars = content.length();
        String truncated = TextTruncator.truncate(content, maxChars, "File: " + pathStr);
        boolean isTruncated = totalChars > maxChars;

        // 8. Format output
        StringBuilder sb = new StringBuilder();
        sb.append("File: ").append(pathStr).append("\n");
        sb.append("Chars: ").append(totalChars);
        if (isTruncated) {
            sb.append(" (truncated to ").append(maxChars).append(")");
        }
        sb.append("\n\n");
        sb.append(truncated);

        // 9. Build relative path for metadata
        String relativePath = context.getWorkspaceGuard().getWorkspaceRoot()
                .relativize(safePath).toString();

        Map<String, Object> metadata = Map.of(
                "path", relativePath,
                "chars", totalChars,
                "truncated", isTruncated
        );

        return ToolResult.success(sb.toString(), metadata);
    }

    /**
     * 从参数 Map 中获取整数值，不存在或为 null 时返回默认值。
     */
    private static int getIntArg(Map<String, Object> args, String key, int defaultValue) {
        Object value = args.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }
}
