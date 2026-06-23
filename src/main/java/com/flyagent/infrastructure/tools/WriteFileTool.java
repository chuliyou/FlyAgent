package com.flyagent.infrastructure.tools;

import com.flyagent.common.exception.SecurityException;
import com.flyagent.domain.tool.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 文件写入工具。
 *
 * <p>在 workspace 内安全写入文本文件。
 * 支持覆盖保护和工作区审批。</p>
 *
 * <h3>参数</h3>
 * <ul>
 *   <li>{@code path} — 文件路径（相对于 workspace），必填</li>
 *   <li>{@code content} — 要写入的文本内容，必填</li>
 *   <li>{@code overwrite} — 是否覆盖已存在文件，默认 false</li>
 * </ul>
 *
 * @author FlyAgent Team
 * @since 1.0
 */
public class WriteFileTool implements Tool {

    @Override
    public String name() {
        return "write_file";
    }

    @Override
    public String description() {
        return "Write or overwrite a text file within the workspace. "
                + "Requires user approval before writing. "
                + "Creates parent directories automatically.";
    }

    @Override
    public ToolDefinition definition() {
        Map<String, JsonSchemaProperty> properties = Map.of(
                "path", new JsonSchemaProperty("string", "Relative path to the file to write"),
                "content", new JsonSchemaProperty("string", "Text content to write to the file"),
                "overwrite", new JsonSchemaProperty("boolean", "Whether to overwrite if file already exists (default: false)")
        );
        ToolParameter parameters = new ToolParameter(properties, List.of("path", "content"));
        return new ToolDefinition(name(), description(), parameters);
    }

    @Override
    public ToolResult execute(ToolCall call, ToolExecutionContext context) {
        Map<String, Object> args = ToolArgumentParser.parseArguments(call);

        // 1. Parse arguments
        String pathStr = (String) args.get("path");
        String content = (String) args.get("content");
        boolean overwrite = getBooleanArg(args, "overwrite", false);

        // 2. Validate required parameters
        if (pathStr == null || pathStr.isBlank()) {
            return ToolResult.error("Required parameter 'path' is missing");
        }
        if (content == null) {
            return ToolResult.error("Required parameter 'content' is missing");
        }

        // 3. Resolve and validate path
        Path safePath;
        try {
            safePath = context.getWorkspaceGuard().resolveAndValidate(pathStr);
        } catch (SecurityException e) {
            return ToolResult.error("Security violation: " + e.getMessage());
        }

        // 4. Check overwrite protection
        boolean exists = Files.exists(safePath);
        if (exists && !overwrite) {
            return ToolResult.error("File already exists. Set overwrite=true to replace.");
        }

        // 5. Require write approval
        if (context.isRequireWriteApproval() && context.getApprovalHandler() != null) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("path", pathStr);
            details.put("size", content.length());
            details.put("overwrite", exists);
            ApprovalRequest approvalRequest = new ApprovalRequest(
                    "write_file",
                    "Write file: " + pathStr + " (" + content.length() + " chars)",
                    details
            );
            if (!context.getApprovalHandler().approve(approvalRequest)) {
                return ToolResult.error("Write operation rejected by user.");
            }
        }

        // 6. Create parent directories and write
        try {
            Path parent = safePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(safePath, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return ToolResult.error("Failed to write file: " + pathStr + " (" + e.getMessage() + ")");
        }

        // 7. Format result
        StringBuilder sb = new StringBuilder();
        sb.append("File written: ").append(pathStr).append("\n");
        sb.append("Chars: ").append(content.length()).append("\n");
        sb.append("Overwritten: ").append(exists);

        Map<String, Object> metadata = Map.of(
                "path", pathStr,
                "chars", content.length(),
                "overwritten", exists
        );

        return ToolResult.success(sb.toString(), metadata);
    }

    /**
     * 从参数 Map 中获取布尔值，不存在或为 null 时返回默认值。
     */
    private static boolean getBooleanArg(Map<String, Object> args, String key, boolean defaultValue) {
        Object value = args.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return defaultValue;
    }
}
