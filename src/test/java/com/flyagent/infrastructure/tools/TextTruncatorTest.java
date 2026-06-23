package com.flyagent.infrastructure.tools;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TextTruncator 单元测试。
 *
 * @author FlyAgent Team
 * @since 1.0
 */
class TextTruncatorTest {

    @Test
    @DisplayName("超长文本被截断并附加截断标记")
    void shouldTruncateLongText() {
        String input = "a".repeat(100);
        String result = TextTruncator.truncate(input, 50, "test_label");

        assertThat(result).contains("[... truncated at 50");
        assertThat(result).contains("total 100 characters");
        assertThat(result).startsWith("a".repeat(50));
        assertThat(result).isNotEqualTo(input);
    }

    @Test
    @DisplayName("短文本不截断，原样返回")
    void shouldNotTruncateShortText() {
        String result = TextTruncator.truncate("hello", 100, "label");

        assertThat(result).isEqualTo("hello");
    }

    @Test
    @DisplayName("null 输入返回空字符串")
    void shouldHandleNullInput() {
        String result = TextTruncator.truncate(null, 100, "label");

        assertThat(result).isEqualTo("");
    }

    @Test
    @DisplayName("文本长度等于 maxChars 时不截断")
    void shouldHandleExactBoundary() {
        String input = "x".repeat(50);

        String result = TextTruncator.truncate(input, 50, "label");

        assertThat(result).isEqualTo(input);
    }

    @Test
    @DisplayName("文本长度等于 maxChars+1 时触发截断")
    void shouldHandleBoundaryPlusOne() {
        String input = "x".repeat(51);

        String result = TextTruncator.truncate(input, 50, "label");

        assertThat(result).isNotEqualTo(input);
        assertThat(result).contains("[... truncated at 50");
        assertThat(result).startsWith("x".repeat(50));
    }
}
