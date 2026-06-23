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
 * CreateProjectStructureTool 单元测试。
 *
 * @author FlyAgent Team
 * @since 1.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CreateProjectStructureToolTest {

    @TempDir
    Path tempDir;

    @Mock
    private ApprovalHandler approvalHandler;

    private CreateProjectStructureTool tool;
    private ToolExecutionContext context;
    private WorkspaceGuard guard;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @BeforeEach
    void setUp() {
        tool = new CreateProjectStructureTool();
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
    @DisplayName("创建 maven-java 项目，生成 pom.xml / App.java / AppTest.java / README.md 四个文件")
    void shouldCreateMavenJavaProject() throws Exception {
        ToolCall call = createToolCall("create_project_structure", Map.of(
                "projectType", "maven-java",
                "basePath", ".",
                "groupId", "com.test",
                "artifactId", "myapp"
        ));
        ToolResult result = tool.execute(call, context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(tempDir.resolve("pom.xml")).exists();
        assertThat(tempDir.resolve("src/main/java/com/test/App.java")).exists();
        assertThat(tempDir.resolve("src/test/java/com/test/AppTest.java")).exists();
        assertThat(tempDir.resolve("README.md")).exists();
    }

    @Test
    @DisplayName("不支持的 projectType 返回 Unsupported 错误")
    void shouldReturnErrorForUnsupportedProjectType() {
        ToolCall call = createToolCall("create_project_structure", Map.of(
                "projectType", "rust-cargo"
        ));
        ToolResult result = tool.execute(call, context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains("Unsupported");
    }

    @Test
    @DisplayName("文件已存在且 overwrite=false 时，跳过已有文件并在摘要中标记 Skipped")
    void shouldSkipExistingFilesWhenNoOverwrite() {
        // First creation
        ToolCall firstCall = createToolCall("create_project_structure", Map.of(
                "projectType", "maven-java",
                "basePath", ".",
                "groupId", "com.test",
                "artifactId", "myapp"
        ));
        tool.execute(firstCall, context);

        // Second creation without overwrite
        ToolCall secondCall = createToolCall("create_project_structure", Map.of(
                "projectType", "maven-java",
                "basePath", ".",
                "groupId", "com.test",
                "artifactId", "myapp",
                "overwrite", false
        ));
        ToolResult result = tool.execute(secondCall, context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getContent()).contains("Skipped");
    }

    @Test
    @DisplayName("overwrite=true 时覆盖已有文件内容")
    void shouldOverwriteWhenFlagSet() throws Exception {
        // Create with original groupId
        ToolCall firstCall = createToolCall("create_project_structure", Map.of(
                "projectType", "maven-java",
                "basePath", ".",
                "groupId", "com.orig",
                "artifactId", "myapp"
        ));
        tool.execute(firstCall, context);

        // Recreate with new groupId and overwrite
        ToolCall secondCall = createToolCall("create_project_structure", Map.of(
                "projectType", "maven-java",
                "basePath", ".",
                "groupId", "com.new",
                "artifactId", "myapp",
                "overwrite", true
        ));
        ToolResult result = tool.execute(secondCall, context);

        assertThat(result.isSuccess()).isTrue();
        String pomContent = Files.readString(tempDir.resolve("pom.xml"), StandardCharsets.UTF_8);
        assertThat(pomContent).contains("com.new");
    }

    @Test
    @DisplayName("basePath 路径遍历攻击被拒绝")
    void shouldRejectPathTraversal() {
        ToolCall call = createToolCall("create_project_structure", Map.of(
                "projectType", "maven-java",
                "basePath", "../../../outside"
        ));
        ToolResult result = tool.execute(call, context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains("outside workspace");
    }

    @Test
    @DisplayName("审批被拒绝时，返回错误提示操作被驳回")
    void shouldRejectOnApprovalDenied() {
        Mockito.when(approvalHandler.approve(Mockito.any())).thenReturn(false);

        ToolCall call = createToolCall("create_project_structure", Map.of(
                "projectType", "maven-java",
                "basePath", ".",
                "groupId", "com.test",
                "artifactId", "myapp"
        ));
        ToolResult result = tool.execute(call, context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains("rejected");
    }

    @Test
    @DisplayName("仅传入 projectType 时使用默认 groupId=com.example / artifactId=demo")
    void shouldUseDefaultsForOptionalParams() throws Exception {
        ToolCall call = createToolCall("create_project_structure", Map.of(
                "projectType", "maven-java"
        ));
        ToolResult result = tool.execute(call, context);

        assertThat(result.isSuccess()).isTrue();
        // Default groupId="com.example" → path uses com/example
        assertThat(result.getContent()).contains("src/main/java/com/example/App.java");
        // Default artifactId="demo" → README.md title
        String readmeContent = Files.readString(tempDir.resolve("README.md"), StandardCharsets.UTF_8);
        assertThat(readmeContent).contains("# demo");
    }
}
