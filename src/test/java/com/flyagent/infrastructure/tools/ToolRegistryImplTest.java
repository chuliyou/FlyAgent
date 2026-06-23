package com.flyagent.infrastructure.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flyagent.common.exception.SecurityException;
import com.flyagent.domain.tool.Tool;
import com.flyagent.domain.tool.ToolCall;
import com.flyagent.domain.tool.ToolDefinition;
import com.flyagent.domain.tool.ToolExecutionContext;
import com.flyagent.domain.tool.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * ToolRegistryImpl 单元测试。
 *
 * @author FlyAgent Team
 * @since 1.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ToolRegistryImplTest {

    @TempDir
    Path workspace;

    @Mock
    private Tool mockTool;

    private ToolRegistryImpl registry;
    private ToolExecutionContext context;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistryImpl();
        context = ToolExecutionContext.builder(workspace).build();
        when(mockTool.name()).thenReturn("test_tool");
        lenient().when(mockTool.definition()).thenReturn(
                new ToolDefinition("test_tool", "A test tool", null));
    }

    private ToolCall createToolCall(String toolName, Map<String, Object> args) {
        try {
            String argsJson = new ObjectMapper().writeValueAsString(args);
            return new ToolCall("call_001", "function",
                    new ToolCall.FunctionCall(toolName, argsJson));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("注册工具后可通过名称查找")
    void shouldRegisterAndFindTool() {
        registry.register(mockTool);

        Optional<Tool> found = registry.find("test_tool");
        assertThat(found).isPresent();
        assertThat(found.get()).isSameAs(mockTool);
    }

    @Test
    @DisplayName("查找不存在的工具返回 Optional.empty()")
    void shouldReturnEmptyForUnknownTool() {
        Optional<Tool> found = registry.find("nonexistent");
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("重复注册同名工具抛出 IllegalArgumentException")
    void shouldThrowOnDuplicateRegistration() {
        registry.register(mockTool);

        Tool anotherMock = org.mockito.Mockito.mock(Tool.class);
        when(anotherMock.name()).thenReturn("test_tool");

        assertThatThrownBy(() -> registry.register(anotherMock))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tool already registered: test_tool");
    }

    @Test
    @DisplayName("注册多个工具后 definitions 返回所有工具定义")
    void shouldReturnAllDefinitions() {
        Tool mockTool2 = org.mockito.Mockito.mock(Tool.class);
        when(mockTool2.name()).thenReturn("another_tool");
        lenient().when(mockTool2.definition()).thenReturn(
                new ToolDefinition("another_tool", "Another tool", null));

        registry.register(mockTool);
        registry.register(mockTool2);

        assertThat(registry.definitions()).hasSize(2);
    }

    @Test
    @DisplayName("执行已注册工具返回工具的执行结果")
    void shouldExecuteRegisteredTool() {
        ToolCall call = createToolCall("test_tool", Map.of("key", "value"));
        when(mockTool.execute(call, context)).thenReturn(ToolResult.success("ok"));

        registry.register(mockTool);
        ToolResult result = registry.execute(call, context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getContent()).isEqualTo("ok");
    }

    @Test
    @DisplayName("执行未注册工具返回错误结果")
    void shouldReturnErrorForUnknownToolOnExecute() {
        ToolCall call = createToolCall("nonexistent", Map.of());

        ToolResult result = registry.execute(call, context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains("Unknown tool");
    }

    @Test
    @DisplayName("工具抛出 SecurityException 时封装为错误结果并标记 security_violation")
    void shouldCatchSecurityException() {
        ToolCall call = createToolCall("test_tool", Map.of());
        when(mockTool.execute(call, context)).thenThrow(new SecurityException("blocked"));

        registry.register(mockTool);
        ToolResult result = registry.execute(call, context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).isEqualTo("blocked");
        assertThat(result.getMetadata()).containsEntry("security_violation", true);
    }

    @Test
    @DisplayName("工具抛出普通异常时封装为错误结果（不向上抛出）")
    void shouldCatchGeneralException() {
        ToolCall call = createToolCall("test_tool", Map.of());
        when(mockTool.execute(call, context)).thenThrow(new RuntimeException("boom"));

        registry.register(mockTool);
        ToolResult result = registry.execute(call, context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).isEqualTo("Tool execution failed: boom");
        assertThat(result.getMetadata()).containsEntry("exception_type", "RuntimeException");
    }

    @Test
    @DisplayName("registerAll 批量注册多个工具")
    void shouldRegisterAll() {
        Tool mockTool2 = org.mockito.Mockito.mock(Tool.class);
        when(mockTool2.name()).thenReturn("tool_2");
        lenient().when(mockTool2.definition()).thenReturn(
                new ToolDefinition("tool_2", "Tool 2", null));

        registry.registerAll(mockTool, mockTool2);

        assertThat(registry.find("test_tool")).isPresent();
        assertThat(registry.find("tool_2")).isPresent();
    }
}
