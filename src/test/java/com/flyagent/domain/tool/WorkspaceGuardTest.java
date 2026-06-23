package com.flyagent.domain.tool;

import com.flyagent.common.exception.SecurityException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * WorkspaceGuard 单元测试。
 *
 * @author FlyAgent Team
 * @since 1.0
 */
class WorkspaceGuardTest {

    @TempDir
    Path tempDir;

    private WorkspaceGuard guard;

    @BeforeEach
    void setUp() {
        guard = new WorkspaceGuard(tempDir);
    }

    @Test
    @DisplayName("解析合法的相对路径返回规范化后的绝对路径")
    void shouldResolveValidRelativePath() {
        Path result = guard.resolveAndValidate("subdir/file.txt");

        assertThat(result).isEqualTo(tempDir.resolve("subdir/file.txt").normalize());
    }

    @Test
    @DisplayName("路径遍历攻击（..）被拒绝，抛出 SecurityException")
    void shouldBlockPathTraversalWithDots() {
        assertThatThrownBy(() -> guard.resolveAndValidate("../../../etc/passwd"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("outside workspace");
    }

    @Test
    @DisplayName("validate 拒绝 workspace 外的绝对路径")
    void shouldBlockAbsolutePathInValidate() {
        Path outside = Path.of("C:\\Windows");

        assertThatThrownBy(() -> guard.validate(outside))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("outside workspace");
    }

    @Test
    @DisplayName("解析正常嵌套子目录返回合法路径")
    void shouldAllowNormalNestedPath() throws IOException {
        Files.createDirectories(tempDir.resolve("subdir"));

        Path result = guard.resolveAndValidate("subdir");

        assertThat(result).isEqualTo(tempDir.resolve("subdir").normalize());
    }

    @Test
    @DisplayName("validate 接受 workspace 内已有文件不抛异常")
    void shouldValidateExistingPath() throws IOException {
        Path file = tempDir.resolve("test.txt");
        Files.createFile(file);

        // should not throw
        guard.validate(file);
    }

    @Test
    @DisplayName("validate 拒绝 workspace 外的路径")
    void shouldRejectOutsidePathInValidate() {
        Path outside = tempDir.resolveSibling("outside");

        assertThatThrownBy(() -> guard.validate(outside))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("outside workspace");
    }

    @Test
    @DisplayName("isWithinWorkspace 对 workspace 内路径返回 true")
    void shouldDetectPathWithinWorkspace() {
        assertThat(guard.isWithinWorkspace(tempDir)).isTrue();
        assertThat(guard.isWithinWorkspace(tempDir.resolve("subdir"))).isTrue();
    }

    @Test
    @DisplayName("isWithinWorkspace 对 workspace 外路径返回 false")
    void shouldDetectPathOutsideWorkspace() {
        assertThat(guard.isWithinWorkspace(Path.of("C:\\Windows"))).isFalse();
    }

    @Test
    @DisplayName("解析空字符串返回 workspace 根路径")
    void shouldHandleEmptyPath() {
        Path result = guard.resolveAndValidate("");

        assertThat(result).isEqualTo(tempDir.toAbsolutePath().normalize());
    }
}
