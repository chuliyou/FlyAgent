package com.flyagent.domain.tool;

import com.flyagent.domain.message.ToolMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ToolResult 单元测试。
 *
 * @author FlyAgent Team
 * @since 1.0
 */
class ToolResultTest {

    @Test
    @DisplayName("构造成功结果：isSuccess=true，getContent 正确，getError=null，metadata 为空")
    void shouldCreateSuccessResult() {
        ToolResult result = ToolResult.success("content");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getContent()).isEqualTo("content");
        assertThat(result.getError()).isNull();
        assertThat(result.getMetadata()).isEmpty();
    }

    @Test
    @DisplayName("构造成功结果（带元数据）：metadata 包含正确键值")
    void shouldCreateSuccessResultWithMetadata() {
        ToolResult result = ToolResult.success("data", Map.of("key", "val"));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getContent()).isEqualTo("data");
        assertThat(result.getMetadata()).containsEntry("key", "val");
    }

    @Test
    @DisplayName("构造失败结果：isSuccess=false，getError 正确，getContent=null")
    void shouldCreateErrorResult() {
        ToolResult result = ToolResult.error("fail");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).isEqualTo("fail");
        assertThat(result.getContent()).isNull();
    }

    @Test
    @DisplayName("构造失败结果（带元数据）：metadata 包含正确键值")
    void shouldCreateErrorResultWithMetadata() {
        ToolResult result = ToolResult.error("boom", Map.of("code", 500));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).isEqualTo("boom");
        assertThat(result.getMetadata()).containsEntry("code", 500);
    }

    @Test
    @DisplayName("metadata 含 truncated=true 时 isTruncated 返回 true，否则返回 false")
    void shouldDetectTruncated() {
        ToolResult truncated = ToolResult.success("data", Map.of("truncated", true));
        ToolResult notTruncated1 = ToolResult.success("data");
        ToolResult notTruncated2 = ToolResult.success("data", Map.of("truncated", false));

        assertThat(truncated.isTruncated()).isTrue();
        assertThat(notTruncated1.isTruncated()).isFalse();
        assertThat(notTruncated2.isTruncated()).isFalse();
    }

    @Test
    @DisplayName("成功结果 toToolMessage 返回 content 为原始内容的 ToolMessage")
    void shouldConvertToToolMessageOnSuccess() {
        ToolResult result = ToolResult.success("hello world");
        ToolMessage msg = result.toToolMessage("call_1");

        assertThat(msg.getToolCallId()).isEqualTo("call_1");
        assertThat(msg.getContent()).isEqualTo("hello world");
    }

    @Test
    @DisplayName("失败结果 toToolMessage 返回 content 以 'Error: ' 开头的 ToolMessage")
    void shouldConvertToToolMessageOnError() {
        ToolResult result = ToolResult.error("fail");
        ToolMessage msg = result.toToolMessage("call_1");

        assertThat(msg.getToolCallId()).isEqualTo("call_1");
        assertThat(msg.getContent()).isEqualTo("Error: fail");
    }

    @Test
    @DisplayName("metadata 为不可修改 Map，修改将抛出 UnsupportedOperationException")
    void metadataShouldBeUnmodifiable() {
        ToolResult result = ToolResult.success("data", Map.of("key", "val"));
        Map<String, Object> metadata = result.getMetadata();

        assertThatThrownBy(() -> metadata.put("newKey", "newVal"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
