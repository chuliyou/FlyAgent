package com.flyagent.domain.agent;

import com.flyagent.domain.chat.ChatRequest;
import com.flyagent.domain.message.Message;
import com.flyagent.domain.message.MessageRole;
import com.flyagent.domain.tool.ToolDefinition;
import com.flyagent.domain.tool.ToolParameter;
import com.flyagent.domain.tool.JsonSchemaProperty;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ContextBuildService 单元测试。
 */
class ContextBuildServiceTest {

    private ContextBuildService service;
    private Conversation conversation;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        service = new ContextBuildService();
        conversation = new Conversation(20, 4000);
    }

    @Test
    void shouldBuildChatRequestWithSystemPromptFirst() {
        ChatRequest request = service.build(conversation, List.of(), tempDir);

        assertThat(request.getMessages()).isNotEmpty();
        Message first = request.getMessages().get(0);
        assertThat(first.getRole()).isEqualTo(MessageRole.SYSTEM);
    }

    @Test
    void shouldIncludeWorkspacePathInSystemPrompt() {
        ChatRequest request = service.build(conversation, List.of(), tempDir);

        String systemContent = ((com.flyagent.domain.message.SystemMessage) request.getMessages().get(0)).getContent();
        assertThat(systemContent).contains(tempDir.toAbsolutePath().toString());
        assertThat(systemContent).contains("FlyAgent");
        assertThat(systemContent).contains("ReAct");
    }

    @Test
    void shouldInjectToolDefinitions() {
        ToolParameter param = new ToolParameter(
                Map.of("path", new JsonSchemaProperty("string", "File path")),
                List.of("path"));
        ToolDefinition toolDef = new ToolDefinition("read_file", "Read a file", param);
        List<ToolDefinition> tools = List.of(toolDef);

        ChatRequest request = service.build(conversation, tools, tempDir);

        assertThat(request.getTools()).hasSize(1);
        assertThat(request.getTools().get(0).getFunction().getName()).isEqualTo("read_file");
    }

    @Test
    void shouldBuildWithEmptyToolsList() {
        ChatRequest request = service.build(conversation, null, tempDir);

        assertThat(request.getTools()).isNull();
    }

    @Test
    void shouldPreserveHistoryMessageOrder() {
        conversation.addUserMessage("user msg");
        conversation.addAssistantMessage("assistant msg");

        ChatRequest request = service.build(conversation, List.of(), tempDir);

        List<Message> messages = request.getMessages();
        // System + 2 history messages
        assertThat(messages).hasSize(3);
        assertThat(messages.get(0).getRole()).isEqualTo(MessageRole.SYSTEM);
        assertThat(messages.get(1).getRole()).isEqualTo(MessageRole.USER);
        assertThat(messages.get(2).getRole()).isEqualTo(MessageRole.ASSISTANT);
    }
}
