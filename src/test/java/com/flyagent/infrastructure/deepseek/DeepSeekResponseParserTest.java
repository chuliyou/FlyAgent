package com.flyagent.infrastructure.deepseek;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flyagent.domain.chat.ChatResponse;
import com.flyagent.domain.message.AssistantMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * DeepSeekResponseParser 单元测试。
 *
 * @author FlyAgent Team
 * @since 1.0
 */
class DeepSeekResponseParserTest {

    private DeepSeekResponseParser parser;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        parser = new DeepSeekResponseParser(objectMapper);
    }

    @Test
    @DisplayName("纯文本回复应正确解析")
    void shouldParsePlainTextResponse() {
        String json = """
                {
                  "id": "chat_abc123",
                  "model": "deepseek-v4-pro",
                  "choices": [
                    {
                      "index": 0,
                      "message": {
                        "role": "assistant",
                        "content": "This is a Java Maven project."
                      },
                      "finish_reason": "stop"
                    }
                  ],
                  "usage": {
                    "prompt_tokens": 100,
                    "completion_tokens": 50,
                    "total_tokens": 150
                  }
                }""";

        ChatResponse response = parser.parse(json);

        assertThat(response.getId()).isEqualTo("chat_abc123");
        assertThat(response.getModel()).isEqualTo("deepseek-v4-pro");
        assertThat(response.firstChoice()).isNotNull();
        assertThat(response.firstChoice().getFinishReason()).isEqualTo("stop");

        AssistantMessage msg = response.firstChoice().getMessage();
        assertThat(msg.hasContent()).isTrue();
        assertThat(msg.getContent()).isEqualTo("This is a Java Maven project.");
        assertThat(msg.hasToolCalls()).isFalse();

        assertThat(response.getUsage().getPromptTokens()).isEqualTo(100);
        assertThat(response.getUsage().getCompletionTokens()).isEqualTo(50);
        assertThat(response.getUsage().getTotalTokens()).isEqualTo(150);
    }

    @Test
    @DisplayName("tool_calls 响应应正确解析")
    void shouldParseToolCallsResponse() {
        String json = """
                {
                  "id": "chat_def456",
                  "model": "deepseek-v4-pro",
                  "choices": [
                    {
                      "index": 0,
                      "message": {
                        "role": "assistant",
                        "tool_calls": [
                          {
                            "id": "call_001",
                            "type": "function",
                            "function": {
                              "name": "read_file",
                              "arguments": "{\\\"path\\\":\\\"pom.xml\\\"}"
                            }
                          }
                        ]
                      },
                      "finish_reason": "tool_calls"
                    }
                  ],
                  "usage": {
                    "prompt_tokens": 80,
                    "completion_tokens": 30,
                    "total_tokens": 110
                  }
                }""";

        ChatResponse response = parser.parse(json);

        assertThat(response.firstChoice().getFinishReason()).isEqualTo("tool_calls");

        AssistantMessage msg = response.firstChoice().getMessage();
        assertThat(msg.hasToolCalls()).isTrue();
        assertThat(msg.getToolCalls()).hasSize(1);
        assertThat(msg.getToolCalls().get(0).getFunction().getName()).isEqualTo("read_file");
    }

    @Test
    @DisplayName("content 为 JSON null 时应正常解析，不抛 NPE")
    void shouldHandleNullContent() {
        String json = """
                {
                  "id": "chat_null",
                  "model": "deepseek-v4-pro",
                  "choices": [
                    {
                      "index": 0,
                      "message": {
                        "role": "assistant",
                        "content": null,
                        "tool_calls": [
                          {
                            "id": "call_002",
                            "type": "function",
                            "function": {
                              "name": "list_files",
                              "arguments": "{\\\"path\\\":\\\".\\\"}"
                            }
                          }
                        ]
                      },
                      "finish_reason": "tool_calls"
                    }
                  ],
                  "usage": {
                    "prompt_tokens": 60,
                    "completion_tokens": 20,
                    "total_tokens": 80
                  }
                }""";

        ChatResponse response = parser.parse(json);

        AssistantMessage msg = response.firstChoice().getMessage();
        assertThat(msg.hasContent()).isFalse();
        assertThat(msg.hasToolCalls()).isTrue();
        assertThat(msg.getToolCalls().get(0).getFunction().getName()).isEqualTo("list_files");
    }

    @Test
    @DisplayName("非法 JSON 应抛出 ApiException")
    void shouldThrowOnInvalidJson() {
        assertThatThrownBy(() -> parser.parse("not a json"))
                .isInstanceOf(com.flyagent.common.exception.ApiException.class)
                .hasMessageContaining("Failed to parse DeepSeek response");
    }
}
