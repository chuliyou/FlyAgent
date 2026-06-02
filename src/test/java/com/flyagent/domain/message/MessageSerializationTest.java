package com.flyagent.domain.message;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 消息序列化/反序列化单元测试。
 *
 * @author FlyAgent Team
 * @since 1.0
 */
class MessageSerializationTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("SystemMessage 序列化后应包含 role 和 content")
    void shouldSerializeSystemMessage() throws Exception {
        SystemMessage msg = new SystemMessage("You are FlyAgent");

        String json = objectMapper.writeValueAsString(msg);

        assertThat(json).contains("\"role\":\"system\"");
        assertThat(json).contains("\"content\":\"You are FlyAgent\"");
    }

    @Test
    @DisplayName("UserMessage 序列化后应包含 role 和 content")
    void shouldSerializeUserMessage() throws Exception {
        UserMessage msg = new UserMessage("帮我分析项目结构");

        String json = objectMapper.writeValueAsString(msg);

        assertThat(json).contains("\"role\":\"user\"");
        assertThat(json).contains("\"content\":\"帮我分析项目结构\"");
    }

    @Test
    @DisplayName("AssistantMessage 纯文本序列化后不应包含 tool_calls")
    void shouldSerializeAssistantTextMessage() throws Exception {
        AssistantMessage msg = AssistantMessage.ofText("这是一个项目分析结果");

        String json = objectMapper.writeValueAsString(msg);

        assertThat(json).contains("\"role\":\"assistant\"");
        assertThat(json).contains("\"content\":\"这是一个项目分析结果\"");
        assertThat(json).doesNotContain("tool_calls");
    }

    @Test
    @DisplayName("AssistantMessage 的 hasToolCalls 应在无 tool calls 时返回 false")
    void shouldReturnFalseWhenNoToolCalls() {
        AssistantMessage msg = AssistantMessage.ofText("hello");

        assertThat(msg.hasToolCalls()).isFalse();
        assertThat(msg.hasContent()).isTrue();
    }

    @Test
    @DisplayName("ToolMessage 序列化后应包含 tool_call_id 和 content")
    void shouldSerializeToolMessage() throws Exception {
        ToolMessage msg = new ToolMessage("call_abc123", "pom.xml\nsrc/");

        String json = objectMapper.writeValueAsString(msg);

        assertThat(json).contains("\"role\":\"tool\"");
        assertThat(json).contains("\"tool_call_id\":\"call_abc123\"");
        assertThat(json).contains("\"content\":\"pom.xml\\nsrc/\"");
    }

    @Test
    @DisplayName("反序列化时应根据 role 路由到正确的 UserMessage 类型")
    void shouldDeserializeUserMessageByRole() throws Exception {
        String json = "{\"role\":\"user\",\"content\":\"hello\"}";

        Message msg = objectMapper.readValue(json, Message.class);

        assertThat(msg).isInstanceOf(UserMessage.class);
        assertThat(msg.getRole()).isEqualTo(MessageRole.USER);
        assertThat(((UserMessage) msg).getContent()).isEqualTo("hello");
    }

    @Test
    @DisplayName("反序列化时应根据 role 路由到正确的 SystemMessage 类型")
    void shouldDeserializeSystemMessageByRole() throws Exception {
        String json = "{\"role\":\"system\",\"content\":\"You are helpful\"}";

        Message msg = objectMapper.readValue(json, Message.class);

        assertThat(msg).isInstanceOf(SystemMessage.class);
        assertThat(msg.getRole()).isEqualTo(MessageRole.SYSTEM);
    }

    @Test
    @DisplayName("MessageRole 枚举序列化时应输出小写值")
    void shouldSerializeRoleAsLowerCase() throws Exception {
        String json = objectMapper.writeValueAsString(MessageRole.ASSISTANT);

        assertThat(json).isEqualTo("\"assistant\"");
    }
}
