package com.flyagent.infrastructure.tools;

import com.flyagent.common.exception.SecurityException;
import com.flyagent.domain.tool.*;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Shell 命令执行工具。
 *
 * <p>在 workspace 目录内执行 Shell 命令，支持超时控制、安全检查和用户审批。
 * stdout 和 stderr 各自截断至 15000 字符。</p>
 *
 * <h3>参数</h3>
 * <ul>
 *   <li>{@code command} — 要执行的 Shell 命令，必填</li>
 *   <li>{@code timeoutSeconds} — 超时秒数（1~600），默认 120</li>
 * </ul>
 *
 * <h3>安全检查</h3>
 * <p>执行前通过 {@link CommandSafetyChecker} 检测危险命令模式，
 * 如 rm -rf /、shutdown 等，命中则拒绝执行。</p>
 *
 * @author FlyAgent Team
 * @since 1.0
 */
public class RunShellTool implements Tool {

    private static final int DEFAULT_TIMEOUT_SECONDS = 120;
    private static final int MIN_TIMEOUT = 1;
    private static final int MAX_TIMEOUT = 600;
    private static final int MAX_OUTPUT_CHARS = 15000;

    @Override
    public String name() {
        return "run_shell";
    }

    @Override
    public String description() {
        return "Execute a shell command within the workspace directory. "
                + "Commands are subject to safety checks and approval. "
                + "Default timeout is 120 seconds. "
                + "Output is truncated at 30000 characters.";
    }

    @Override
    public ToolDefinition definition() {
        Map<String, JsonSchemaProperty> properties = Map.of(
                "command", new JsonSchemaProperty("string", "The shell command to execute"),
                "timeoutSeconds", new JsonSchemaProperty("integer",
                        "Timeout in seconds (1-" + MAX_TIMEOUT + ", default: " + DEFAULT_TIMEOUT_SECONDS + ")")
        );
        ToolParameter parameters = new ToolParameter(properties, List.of("command"));
        return new ToolDefinition(name(), description(), parameters);
    }

    @Override
    public ToolResult execute(ToolCall call, ToolExecutionContext context) {
        try {
            return doExecute(call, context);
        } catch (Exception e) {
            return ToolResult.error("Shell execution failed: " + e.getMessage());
        }
    }

    private ToolResult doExecute(ToolCall call, ToolExecutionContext context) {
        Map<String, Object> args = ToolArgumentParser.parseArguments(call);

        // 1. 解析参数
        String command = (String) args.get("command");
        int timeoutSeconds = getIntArg(args, "timeoutSeconds", DEFAULT_TIMEOUT_SECONDS);

        // 2. 校验必填参数
        if (command == null || command.isBlank()) {
            return ToolResult.error("Required parameter 'command' is missing");
        }

        // 3. 钳制超时范围
        timeoutSeconds = Math.max(MIN_TIMEOUT, Math.min(MAX_TIMEOUT, timeoutSeconds));

        // 4. 安全检查
        try {
            new CommandSafetyChecker().check(command);
        } catch (SecurityException e) {
            Map<String, Object> metadata = Map.of("security_violation", true);
            return ToolResult.error("Security violation: " + e.getMessage(), metadata);
        }

        // 5. 审批
        if (context.isRequireShellApproval() && context.getApprovalHandler() != null) {
            ApprovalRequest approvalRequest = new ApprovalRequest(
                    "run_shell",
                    "Execute: " + command,
                    Map.of("command", command, "timeoutSeconds", timeoutSeconds)
            );
            if (!context.getApprovalHandler().approve(approvalRequest)) {
                return ToolResult.error("Shell execution rejected by user.");
            }
        }

        // 6. OS 检测与命令构造
        String osName = System.getProperty("os.name").toLowerCase();
        String[] cmdArgs;
        if (osName.contains("win")) {
            cmdArgs = new String[]{"cmd", "/c", command};
        } else {
            cmdArgs = new String[]{"sh", "-c", command};
        }

        // 7. 启动进程
        ProcessBuilder pb = new ProcessBuilder(cmdArgs);
        pb.directory(context.getWorkspace().toFile());
        pb.redirectErrorStream(false);

        long startTime = System.currentTimeMillis();
        Process process;
        try {
            process = pb.start();
        } catch (Exception e) {
            return ToolResult.error("Failed to start process: " + e.getMessage());
        }

        java.io.InputStream stdoutStream = process.getInputStream();
        java.io.InputStream stderrStream = process.getErrorStream();
        try {
            // 8. 等待进程完成（带超时）
            boolean completed;
            try {
                completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
                return ToolResult.error("Shell execution interrupted.");
            }

            // 9. 超时处理
            if (!completed) {
                process.destroyForcibly();
                // 仍尝试读取已捕获的输出
                String partialStdout = readStreamSafely(stdoutStream);
                String partialStderr = readStreamSafely(stderrStream);
                long durationMs = System.currentTimeMillis() - startTime;

                String output = formatOutput(command, -1, durationMs,
                        partialStdout, partialStderr, true);
                Map<String, Object> metadata = new LinkedHashMap<>();
                metadata.put("exitCode", -1);
                metadata.put("durationMs", durationMs);
                metadata.put("timeout", true);
                metadata.put("truncated", isTruncated(output));
                return ToolResult.error("Command timed out after " + timeoutSeconds + " seconds", metadata);
            }

            // 10. 读取输出
            int exitCode = process.exitValue();
            long durationMs = System.currentTimeMillis() - startTime;
            String stdout = readStreamSafely(stdoutStream);
            String stderr = readStreamSafely(stderrStream);

            // 11. 截断输出
            String truncatedStdout = TextTruncator.truncate(stdout, MAX_OUTPUT_CHARS, "STDOUT");
            String truncatedStderr = TextTruncator.truncate(stderr, MAX_OUTPUT_CHARS, "STDERR");
            boolean truncated = stdout.length() > MAX_OUTPUT_CHARS || stderr.length() > MAX_OUTPUT_CHARS;

            // 12. 格式化输出
            String output = formatOutput(command, exitCode, durationMs,
                    truncatedStdout, truncatedStderr, false);

            // 13. 构建结果
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("exitCode", exitCode);
            metadata.put("durationMs", durationMs);
            metadata.put("truncated", truncated);

            if (exitCode == 0) {
                return ToolResult.success(output, metadata);
            } else {
                return ToolResult.error("Command exited with code " + exitCode, metadata);
            }
        } finally {
            closeQuietly(stdoutStream);
            closeQuietly(stderrStream);
        }
    }

    /**
     * 静默关闭流。
     */
    private static void closeQuietly(java.io.InputStream stream) {
        try {
            if (stream != null) {
                stream.close();
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * 安全读取流内容，异常时返回空字符串。
     */
    private static String readStreamSafely(java.io.InputStream stream) {
        try {
            byte[] bytes = stream.readAllBytes();
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 格式化命令输出。
     */
    private static String formatOutput(String command, int exitCode, long durationMs,
                                       String stdout, String stderr, boolean timedOut) {
        StringBuilder sb = new StringBuilder();
        sb.append("Command: ").append(command).append("\n");
        if (timedOut) {
            sb.append("ExitCode: TIMEOUT\n");
        } else {
            sb.append("ExitCode: ").append(exitCode).append("\n");
        }
        sb.append("Duration: ").append(durationMs).append("ms\n");
        sb.append("\nSTDOUT:\n");
        sb.append(stdout.isEmpty() ? "(empty)" : stdout);
        sb.append("\n\nSTDERR:\n");
        sb.append(stderr.isEmpty() ? "(empty)" : stderr);
        return sb.toString();
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

    /**
     * 判断结果是否被截断。
     */
    private static boolean isTruncated(String output) {
        return output.contains("[... truncated at ");
    }
}
