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
 * ListFilesTool 单元测试。
 *
 * @author FlyAgent Team
 * @since 1.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ListFilesToolTest {

    @TempDir
    Path tempDir;

    @Mock
    private ApprovalHandler approvalHandler;

    private ListFilesTool tool;
    private ToolExecutionContext context;
    private WorkspaceGuard guard;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @BeforeEach
    void setUp() {
        tool = new ListFilesTool();
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
    @DisplayName("列出根目录内容，树形输出包含文件和子目录")
    void shouldListRootDirectory() throws Exception {
        Files.createFile(tempDir.resolve("pom.xml"));
        Files.createDirectory(tempDir.resolve("src"));

        ToolCall call = createToolCall("list_files", Map.of("path", "."));
        ToolResult result = tool.execute(call, context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getContent()).contains("pom.xml", "src");
    }

    @Test
    @DisplayName("maxDepth=2 时只显示两层目录，不出现深层子目录")
    void shouldRespectMaxDepth() throws Exception {
        Path deep = tempDir.resolve("a/b/c/d");
        Files.createDirectories(deep);

        ToolCall call = createToolCall("list_files", Map.of("path", ".", "maxDepth", 2));
        ToolResult result = tool.execute(call, context);

        assertThat(result.isSuccess()).isTrue();
        String content = result.getContent();
        // Check tree section only (before summary line), since "d" appears in "directories"
        String treeSection = content.substring(0, content.lastIndexOf("\n"));
        assertThat(treeSection).contains("a", "b");
        assertThat(treeSection).doesNotContain("d/");
    }

    @Test
    @DisplayName("默认忽略 .git 目录")
    void shouldIgnoreDotGitDirectory() throws Exception {
        Files.createDirectories(tempDir.resolve(".git"));
        Files.createFile(tempDir.resolve(".git/config"));

        ToolCall call = createToolCall("list_files", Map.of("path", "."));
        ToolResult result = tool.execute(call, context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getContent()).doesNotContain(".git");
    }

    @Test
    @DisplayName("默认忽略 target 目录")
    void shouldIgnoreTargetDirectory() throws Exception {
        Files.createDirectories(tempDir.resolve("target/classes"));

        ToolCall call = createToolCall("list_files", Map.of("path", "."));
        ToolResult result = tool.execute(call, context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getContent()).doesNotContain("target");
    }

    @Test
    @DisplayName("includeHidden=true 时显示隐藏文件")
    void shouldIncludeHiddenWhenFlagSet() throws Exception {
        Files.createFile(tempDir.resolve(".hidden_file"));

        ToolCall call = createToolCall("list_files", Map.of("path", ".", "includeHidden", true));
        ToolResult result = tool.execute(call, context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getContent()).contains(".hidden_file");
    }

    @Test
    @DisplayName("默认不显示隐藏文件（以 . 开头）")
    void shouldExcludeHiddenByDefault() throws Exception {
        Files.createFile(tempDir.resolve(".hidden_file"));

        ToolCall call = createToolCall("list_files", Map.of("path", "."));
        ToolResult result = tool.execute(call, context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getContent()).doesNotContain(".hidden_file");
    }

    @Test
    @DisplayName("目标路径不是目录时，返回错误")
    void shouldReturnErrorForNonDirectory() throws Exception {
        Files.createFile(tempDir.resolve("somefile.txt"));

        ToolCall call = createToolCall("list_files", Map.of("path", "somefile.txt"));
        ToolResult result = tool.execute(call, context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains("not a directory");
    }

    @Test
    @DisplayName("路径遍历攻击被拒绝")
    void shouldReturnErrorForPathTraversal() {
        ToolCall call = createToolCall("list_files", Map.of("path", "../../../etc"));
        ToolResult result = tool.execute(call, context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains("outside workspace");
    }
}
