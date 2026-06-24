package com.flyagent.domain.agent;

import com.flyagent.domain.message.Message;
import com.flyagent.domain.message.MessageRole;
import com.flyagent.domain.message.AssistantMessage;
import com.flyagent.domain.message.ToolMessage;
import com.flyagent.domain.tool.ToolCall;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Conversation 单元测试。
 */
class ConversationTest {

    private Conversation conversation;

    @BeforeEach
    void setUp() {
        conversation = new Conversation(20, 4000);
    }

    @Test
    void shouldAddUserMessage() {
        conversation.addUserMessage("hello");

        List<Message> messages = conversation.getMessages();
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).getRole()).isEqualTo(MessageRole.USER);
    }

    @Test
    void shouldAddAssistantMessage() {
        conversation.addAssistantMessage("answer");

        List<Message> messages = conversation.getMessages();
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).getRole()).isEqualTo(MessageRole.ASSISTANT);
    }

    @Test
    void shouldAddAssistantToolCalls() {
        ToolCall.FunctionCall fn = new ToolCall.FunctionCall("read_file", "{\"path\":\"pom.xml\"}");
        ToolCall toolCall = new ToolCall("call_1", "function", fn);
        conversation.addAssistantToolCalls(List.of(toolCall));

        List<Message> messages = conversation.getMessages();
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).getRole()).isEqualTo(MessageRole.ASSISTANT);
        assertThat(((AssistantMessage) messages.get(0)).hasToolCalls()).isTrue();
    }

    @Test
    void shouldAddToolMessage() {
        conversation.addToolMessage("call_1", "file content here");

        List<Message> messages = conversation.getMessages();
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).getRole()).isEqualTo(MessageRole.TOOL);
        assertThat(((ToolMessage) messages.get(0)).getContent()).isEqualTo("file content here");
    }

    @Test
    void shouldTruncateLongToolResult() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5000; i++) {
            sb.append("a");
        }
        String longContent = sb.toString();

        conversation.addToolMessage("call_1", longContent);

        List<Message> messages = conversation.getMessages();
        assertThat(messages).hasSize(1);
        String resultContent = ((ToolMessage) messages.get(0)).getContent();
        assertThat(resultContent.length()).isLessThan(4500); // truncated with marker
        assertThat(resultContent).contains("truncated");
    }

    @Test
    void shouldEnforceMessageWindow() {
        // Add 30 messages — only last 20 should be kept
        for (int i = 0; i < 30; i++) {
            conversation.addUserMessage("msg " + i);
        }

        List<Message> messages = conversation.getMessages();
        assertThat(messages.size()).isLessThanOrEqualTo(20);
    }

    @Test
    void shouldPreserveOrderOfMessages() {
        conversation.addUserMessage("first");
        conversation.addAssistantMessage("second");
        conversation.addToolMessage("call_1", "third");

        List<Message> messages = conversation.getMessages();
        assertThat(messages).hasSize(3);
        assertThat(messages.get(0).getRole()).isEqualTo(MessageRole.USER);
        assertThat(messages.get(1).getRole()).isEqualTo(MessageRole.ASSISTANT);
        assertThat(messages.get(2).getRole()).isEqualTo(MessageRole.TOOL);
    }

    @Test
    void shouldClearNonSystemMessages() {
        conversation.addUserMessage("hello");
        conversation.addAssistantMessage("response");

        assertThat(conversation.size()).isEqualTo(2);

        conversation.clear();

        assertThat(conversation.size()).isEqualTo(0);
    }

    @Test
    void shouldReportCorrectSize() {
        assertThat(conversation.size()).isEqualTo(0);

        conversation.addUserMessage("hello");
        assertThat(conversation.size()).isEqualTo(1);

        conversation.addAssistantMessage("world");
        assertThat(conversation.size()).isEqualTo(2);
    }
}
