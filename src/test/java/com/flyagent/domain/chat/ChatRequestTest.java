package com.flyagent.domain.chat;

import com.flyagent.domain.message.SystemMessage;
import com.flyagent.domain.message.UserMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ChatRequest 单元测试。
 *
 * @author FlyAgent Team
 * @since 1.0
 */
class ChatRequestTest {

    @Test
    @DisplayName("缺少 messages 时应抛出 IllegalArgumentException")
    void shouldThrowWhenMessagesIsNull() {
        assertThatThrownBy(() -> ChatRequest.builder().build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("messages must not be empty");
    }

    @Test
    @DisplayName("缺少 messages 时应抛出 IllegalArgumentException（空列表）")
    void shouldThrowWhenMessagesIsEmpty() {
        assertThatThrownBy(() -> ChatRequest.builder()
                .messages(List.of())
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("messages must not be empty");
    }

    @Test
    @DisplayName("Builder 默认值应正确")
    void shouldHaveCorrectDefaults() {
        UserMessage msg = new UserMessage("hello");
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(msg))
                .build();

        assertThat(request.getModel()).isEqualTo("deepseek-v4-pro");
        assertThat(request.isThinking()).isTrue();
        assertThat(request.getReasoningEffort()).isEqualTo("high");
        assertThat(request.isStream()).isFalse();
    }

    @Test
    @DisplayName("可覆盖 Builder 默认值")
    void shouldOverrideDefaults() {
        UserMessage msg = new UserMessage("hello");
        ChatRequest request = ChatRequest.builder()
                .model("deepseek-v4-flash")
                .messages(List.of(msg))
                .thinking(false)
                .reasoningEffort("medium")
                .stream(true)
                .build();

        assertThat(request.getModel()).isEqualTo("deepseek-v4-flash");
        assertThat(request.isThinking()).isFalse();
        assertThat(request.getReasoningEffort()).isEqualTo("medium");
        assertThat(request.isStream()).isTrue();
    }

    @Test
    @DisplayName("正常构造后 messages 应正确保存")
    void shouldPreserveMessages() {
        UserMessage userMsg = new UserMessage("hello");
        SystemMessage sysMsg = new SystemMessage("You are a helpful assistant");

        ChatRequest request = ChatRequest.builder()
                .messages(List.of(sysMsg, userMsg))
                .build();

        assertThat(request.getMessages()).hasSize(2);
        assertThat(request.getMessages().get(0)).isInstanceOf(SystemMessage.class);
        assertThat(request.getMessages().get(1)).isInstanceOf(UserMessage.class);
    }
}
