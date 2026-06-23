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
 * ReadFileTool 单元测试。
 *
 * @author FlyAgent Team
 * @since 1.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReadFileToolTest {

    @TempDir
    Path tempDir;

    @Mock
    private ApprovalHandler approvalHandler;

    private ReadFileTool tool;
    private ToolExecutionContext context;
    private WorkspaceGuard guard;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @BeforeEach
    void setUp() {
        tool = new ReadFileTool();
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
    @DisplayName("读取已存在文件，返回成功并包含文件内容和文件标识")
    void shouldReadExistingFile() throws Exception {
        Files.writeString(tempDir.resolve("test.txt"), "Hello World", StandardCharsets.UTF_8);

        ToolCall call = createToolCall("read_file", Map.of("path", "test.txt"));
        ToolResult result = tool.execute(call, context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getContent()).contains("Hello World", "File: test.txt");
    }

    @Test
    @DisplayName("读取不存在的文件，返回错误提示文件未找到")
    void shouldReturnErrorForMissingFile() {
        ToolCall call = createToolCall("read_file", Map.of("path", "nonexistent.txt"));
        ToolResult result = tool.execute(call, context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains("not found");
    }

    @Test
    @DisplayName("路径为目录时，返回错误提示路径是目录")
    void shouldReturnErrorForDirectory() {
        ToolCall call = createToolCall("read_file", Map.of("path", "."));
        ToolResult result = tool.execute(call, context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains("directory");
    }

    @Test
    @DisplayName("路径遍历攻击被拒绝，返回越权错误")
    void shouldBlockPathTraversal() {
        ToolCall call = createToolCall("read_file", Map.of("path", "../../../etc/passwd"));
        ToolResult result = tool.execute(call, context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains("outside workspace");
    }

    @Test
    @DisplayName("超大文件按 maxChars 截断，标记 isTruncated 为 true")
    void shouldTruncateLargeFile() throws Exception {
        String largeContent = "a".repeat(30000);
        Files.writeString(tempDir.resolve("large.txt"), largeContent, StandardCharsets.UTF_8);

        ToolCall call = createToolCall("read_file", Map.of("path", "large.txt", "maxChars", 1000));
        ToolResult result = tool.execute(call, context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isTruncated()).isTrue();
        assertThat(result.getContent()).hasSizeLessThan(largeContent.length());
    }

    @Test
    @DisplayName("空文件返回成功，Content 包含 File 前缀和 Chars: 0")
    void shouldReadEmptyFile() throws Exception {
        Files.createFile(tempDir.resolve("empty.txt"));

        ToolCall call = createToolCall("read_file", Map.of("path", "empty.txt"));
        ToolResult result = tool.execute(call, context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getContent()).contains("File:", "Chars: 0");
    }

    @Test
    @DisplayName("缺少必填参数 path 时，返回错误提示参数缺失")
    void shouldReturnErrorForMissingPathParam() {
        ToolCall call = createToolCall("read_file", Map.of());
        ToolResult result = tool.execute(call, context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains("missing");
    }
}
