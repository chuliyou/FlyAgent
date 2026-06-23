package com.flyagent.infrastructure.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flyagent.domain.tool.ApprovalHandler;
import com.flyagent.domain.tool.ToolCall;
import com.flyagent.domain.tool.ToolExecutionContext;
import com.flyagent.domain.tool.ToolResult;
import com.flyagent.domain.tool.WorkspaceGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RunShellTool 单元测试。
 *
 * @author FlyAgent Team
 * @since 1.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RunShellToolTest {

    @TempDir
    Path tempDir;

    @Mock
    private ApprovalHandler approvalHandler;

    private RunShellTool tool;
    private ToolExecutionContext context;
    private WorkspaceGuard guard;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @BeforeEach
    void setUp() {
        tool = new RunShellTool();
        guard = new WorkspaceGuard(tempDir);
        Mockito.when(approvalHandler.approve(Mockito.any())).thenReturn(true);
        context = ToolExecutionContext.builder(tempDir)
                .workspaceGuard(guard)
                .approvalHandler(approvalHandler)
                .requireWriteApproval(true)
                .requireShellApproval(true)
                .build();
    }

    private ToolCall createToolCall(String toolName, Map<String, Object> args) {
        try {
            String argsJson = MAPPER.writeValueAsString(args);
            return new ToolCall("call_001", "function",
                    new ToolCall.FunctionCall(toolName, argsJson));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("执行 echo 命令，返回成功，输出包含 hello 且 exitCode 为 0")
    void shouldExecuteEchoCommand() {
        ToolCall call = createToolCall("run_shell", Map.of("command", "echo hello"));
        ToolResult result = tool.execute(call, context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getContent()).contains("hello");
        assertThat(result.getMetadata()).containsEntry("exitCode", 0);
    }

    @Test
    @DisplayName("执行不存在的命令，返回失败")
    void shouldReturnNonZeroExitCodeForFailedCommand() {
        ToolCall call = createToolCall("run_shell", Map.of("command", "nonexistent_command_xyz123"));
        ToolResult result = tool.execute(call, context);

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    @DisplayName("检测到危险命令 rm -rf / 时，拒绝执行并标记 security_violation")
    void shouldBlockDangerousCommand() {
        ToolCall call = createToolCall("run_shell", Map.of("command", "shutdown"));
        ToolResult result = tool.execute(call, context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains("Dangerous command");
        assertThat(result.getMetadata()).containsEntry("security_violation", true);
    }

    @Test
    @DisplayName("审批被拒绝时，返回错误提示操作被驳回")
    void shouldReturnErrorWhenApprovalDenied() {
        Mockito.when(approvalHandler.approve(Mockito.any())).thenReturn(false);

        ToolCall call = createToolCall("run_shell", Map.of("command", "echo test"));
        ToolResult result = tool.execute(call, context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains("rejected");
    }

    @Test
    @DisplayName("命令超时时，返回错误提示超时")
    void shouldHandleCommandWithTimeout() {
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        // Use a cmd-internal loop (no child process) so destroyForcibly properly closes pipes
        String sleepCmd = isWindows ? "for /L %i in (1,1,99999999) do @echo %i" : "sleep 360";

        ToolCall call = createToolCall("run_shell", Map.of(
                "command", sleepCmd,
                "timeoutSeconds", 1
        ));
        ToolResult result = tool.execute(call, context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains("timed out");
    }

    @Test
    @DisplayName("STDOUT 段落包含命令的实际输出内容")
    void shouldCaptureStdout() {
        ToolCall call = createToolCall("run_shell", Map.of("command", "echo stdout_content"));
        ToolResult result = tool.execute(call, context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getContent()).contains("STDOUT");
        assertThat(result.getContent()).contains("stdout_content");
    }

    @Test
    @DisplayName("成功命令的 metadata 中包含 durationMs")
    void shouldIncludeDurationInMetadata() {
        ToolCall call = createToolCall("run_shell", Map.of("command", "echo test"));
        ToolResult result = tool.execute(call, context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMetadata()).containsKey("durationMs");
    }
}
