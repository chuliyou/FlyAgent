package com.flyagent.domain.tool;

import com.flyagent.common.exception.SecurityException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CommandSafetyChecker 单元测试。
 *
 * @author FlyAgent Team
 * @since 1.0
 */
class CommandSafetyCheckerTest {

    private CommandSafetyChecker checker;

    @BeforeEach
    void setUp() {
        checker = new CommandSafetyChecker();
    }

    @Test
    @DisplayName("检测 rm -rf 为危险命令")
    void shouldDetectRmRfRoot() {
        assertThat(checker.isDangerous("rm -rf /etc")).isTrue();
        assertThat(checker.detectDanger("rm -rf /etc")).contains("rm -rf");
    }

    @Test
    @DisplayName("检测 shutdown 为危险命令")
    void shouldDetectShutdown() {
        assertThat(checker.isDangerous("shutdown now")).isTrue();
        assertThat(checker.detectDanger("shutdown now")).isEqualTo("shutdown");
    }

    @Test
    @DisplayName("检测 reboot 为危险命令")
    void shouldDetectReboot() {
        assertThat(checker.isDangerous("reboot")).isTrue();
    }

    @Test
    @DisplayName("检测 git reset --hard 为危险命令")
    void shouldDetectGitResetHard() {
        assertThat(checker.isDangerous("git reset --hard HEAD~1")).isTrue();
        assertThat(checker.detectDanger("git reset --hard HEAD~1")).contains("git reset --hard");
    }

    @Test
    @DisplayName("检测 dd 命令为危险命令")
    void shouldDetectDdCommand() {
        assertThat(checker.isDangerous("dd if=/dev/zero of=/dev/sda")).isTrue();
        assertThat(checker.detectDanger("dd if=/dev/zero of=/dev/sda")).contains("dd if=");
    }

    @Test
    @DisplayName("检测 mkfs 为危险命令")
    void shouldDetectMkfs() {
        assertThat(checker.isDangerous("mkfs.ext4 /dev/sda1")).isTrue();
        assertThat(checker.detectDanger("mkfs.ext4 /dev/sda1")).isEqualTo("mkfs");
    }

    @Test
    @DisplayName("检测 format 磁盘为危险命令")
    void shouldDetectFormatDisk() {
        assertThat(checker.isDangerous("format C:test")).isTrue();
        assertThat(checker.detectDanger("format C:test")).contains("format disk");
    }

    @Test
    @DisplayName("检测 chmod 777 / 为危险命令")
    void shouldDetectChmod777Root() {
        assertThat(checker.isDangerous("chmod 777 /")).isTrue();
        assertThat(checker.detectDanger("chmod 777 /")).contains("chmod 777");
    }

    @Test
    @DisplayName("安全命令不被检测为危险")
    void shouldAllowSafeCommand() {
        assertThat(checker.isDangerous("echo hello")).isFalse();
        assertThat(checker.isDangerous("mvn compile")).isFalse();
        assertThat(checker.isDangerous("ls -la")).isFalse();
    }

    @Test
    @DisplayName("null 命令处理：detectDanger 返回 null，isDangerous 返回 false")
    void shouldHandleNullCommand() {
        assertThat(checker.detectDanger(null)).isNull();
        assertThat(checker.isDangerous(null)).isFalse();
    }

    @Test
    @DisplayName("空命令处理：detectDanger 返回 null")
    void shouldHandleEmptyCommand() {
        assertThat(checker.detectDanger("")).isNull();
        assertThat(checker.detectDanger("   ")).isNull();
    }

    @Test
    @DisplayName("check 检测到危险命令时抛出 SecurityException")
    void shouldThrowOnCheckDangerous() {
        assertThatThrownBy(() -> checker.check("rm -rf /etc"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Dangerous command detected");
    }

    @Test
    @DisplayName("check 检测安全命令时不抛异常")
    void shouldNotThrowOnCheckSafe() {
        assertThatNoException().isThrownBy(() -> checker.check("echo safe"));
    }

    @Test
    @DisplayName("大小写不敏感检测 del /s /q 危险命令")
    void shouldDetectDelSQ() {
        assertThat(checker.isDangerous("del /s /q C:\\")).isTrue();
        assertThat(checker.isDangerous("DEL /S /Q D:\\")).isTrue();
    }
}
