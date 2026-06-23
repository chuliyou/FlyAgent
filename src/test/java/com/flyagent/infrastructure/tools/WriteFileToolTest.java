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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WriteFileTool 单元测试。
 *
 * @author FlyAgent Team
 * @since 1.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WriteFileToolTest {

    @TempDir
    Path tempDir;

    @Mock
    private ApprovalHandler approvalHandler;

    private WriteFileTool tool;
    private ToolExecutionContext context;
    private WorkspaceGuard guard;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @BeforeEach
    void setUp() {
        tool = new WriteFileTool();
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
    @DisplayName("创建新文件，成功写入并验证磁盘文件内容和结果")
    void shouldCreateNewFile() throws Exception {
        ToolCall call = createToolCall("write_file", Map.of(
                "path", "output.txt",
                "content", "test content"
        ));
        ToolResult result = tool.execute(call, context);

        assertThat(result.isSuccess()).isTrue();
        Path file = tempDir.resolve("output.txt");
        assertThat(file).exists();
        assertThat(Files.readString(file, StandardCharsets.UTF_8)).isEqualTo("test content");
    }

    @Test
    @DisplayName("自动创建不存在的父目录并写入文件")
    void shouldCreateParentDirectories() throws Exception {
        ToolCall call = createToolCall("write_file", Map.of(
                "path", "deep/nested/output.txt",
                "content", "nested"
        ));
        ToolResult result = tool.execute(call, context);

        assertThat(result.isSuccess()).isTrue();
        Path file = tempDir.resolve("deep/nested/output.txt");
        assertThat(file).exists();
        assertThat(Files.readString(file, StandardCharsets.UTF_8)).isEqualTo("nested");
    }

    @Test
    @DisplayName("文件已存在且 overwrite=false 时，返回错误提示文件已存在")
    void shouldReturnErrorWhenFileExistsAndNoOverwrite() throws Exception {
        Path existing = tempDir.resolve("output.txt");
        Files.writeString(existing, "old content", StandardCharsets.UTF_8);

        ToolCall call = createToolCall("write_file", Map.of(
                "path", "output.txt",
                "content", "new content",
                "overwrite", false
        ));
        ToolResult result = tool.execute(call, context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains("already exists");
    }

    @Test
    @DisplayName("overwrite=true 时覆盖已有文件")
    void shouldOverwriteWhenFlagSet() throws Exception {
        Path existing = tempDir.resolve("output.txt");
        Files.writeString(existing, "old", StandardCharsets.UTF_8);

        ToolCall call = createToolCall("write_file", Map.of(
                "path", "output.txt",
                "content", "new",
                "overwrite", true
        ));
        ToolResult result = tool.execute(call, context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(Files.readString(existing, StandardCharsets.UTF_8)).isEqualTo("new");
    }

    @Test
    @DisplayName("审批被拒绝时，返回错误提示操作被驳回")
    void shouldReturnErrorWhenApprovalDenied() {
        Mockito.when(approvalHandler.approve(Mockito.any())).thenReturn(false);

        ToolCall call = createToolCall("write_file", Map.of(
                "path", "output.txt",
                "content", "test"
        ));
        ToolResult result = tool.execute(call, context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains("rejected");
    }

    @Test
    @DisplayName("路径遍历攻击被拒绝")
    void shouldBlockPathTraversal() {
        ToolCall call = createToolCall("write_file", Map.of(
                "path", "../../../etc/hosts",
                "content", "malicious"
        ));
        ToolResult result = tool.execute(call, context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains("outside workspace");
    }
}
