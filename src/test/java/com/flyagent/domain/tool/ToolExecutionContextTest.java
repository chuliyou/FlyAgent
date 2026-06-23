package com.flyagent.domain.tool;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ToolExecutionContext 单元测试。
 *
 * @author FlyAgent Team
 * @since 1.0
 */
class ToolExecutionContextTest {

    @TempDir
    Path workspace;

    @Test
    @DisplayName("builder 默认值：timeout=120s，requireShellApproval=true，requireWriteApproval=true，workspaceGuard 不为 null")
    void shouldHaveSensibleDefaults() {
        ToolExecutionContext ctx = ToolExecutionContext.builder(workspace).build();

        assertThat(ctx.getTimeout()).isEqualTo(Duration.ofSeconds(120));
        assertThat(ctx.isRequireShellApproval()).isTrue();
        assertThat(ctx.isRequireWriteApproval()).isTrue();
        assertThat(ctx.getWorkspaceGuard()).isNotNull();
    }

    @Test
    @DisplayName("builder 支持自定义 timeout 和审批开关")
    void shouldAcceptCustomValues() {
        ToolExecutionContext ctx = ToolExecutionContext.builder(workspace)
                .timeout(Duration.ofSeconds(60))
                .requireShellApproval(false)
                .requireWriteApproval(false)
                .build();

        assertThat(ctx.getTimeout()).isEqualTo(Duration.ofSeconds(60));
        assertThat(ctx.isRequireShellApproval()).isFalse();
        assertThat(ctx.isRequireWriteApproval()).isFalse();
    }

    @Test
    @DisplayName("builder 未显式设置 workspaceGuard 时自动创建")
    void shouldAutoCreateWorkspaceGuard() {
        ToolExecutionContext ctx = ToolExecutionContext.builder(workspace).build();

        assertThat(ctx.getWorkspaceGuard()).isNotNull();
        assertThat(ctx.getWorkspaceGuard().getWorkspaceRoot())
                .isEqualTo(workspace.toAbsolutePath().normalize());
    }

    @Test
    @DisplayName("builder 显式设置 workspaceGuard 时保留传入的实例")
    void shouldUseProvidedWorkspaceGuard() {
        Path otherRoot = workspace.resolve("other");
        WorkspaceGuard customGuard = new WorkspaceGuard(otherRoot);

        ToolExecutionContext ctx = ToolExecutionContext.builder(workspace)
                .workspaceGuard(customGuard)
                .build();

        assertThat(ctx.getWorkspaceGuard()).isSameAs(customGuard);
    }
}
