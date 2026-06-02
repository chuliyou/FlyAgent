package com.flyagent.infrastructure.deepseek;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flyagent.common.exception.ApiException;
import com.flyagent.domain.chat.ChatChoice;
import com.flyagent.domain.chat.ChatResponse;
import com.flyagent.domain.chat.TokenUsage;
import com.flyagent.domain.message.AssistantMessage;
import com.flyagent.domain.tool.ToolCall;

import java.util.ArrayList;
import java.util.List;

/**
 * DeepSeek 响应解析器。
 *
 * <p>将 API 原始 JSON 字符串解析为领域 ChatResponse 对象。
 * 支持纯文本回复和工具调用两种响应格式。</p>
 *
 * @author FlyAgent Team
 * @since 1.0
 */
public class DeepSeekResponseParser {

    private final ObjectMapper objectMapper;

    public DeepSeekResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 解析 API 响应 JSON。
     *
     * @param responseJson 原始 JSON 字符串
     * @return ChatResponse 领域对象
     * @throws ApiException 当解析失败时
     */
    public ChatResponse parse(String responseJson) {
        try {
            JsonNode root = objectMapper.readTree(responseJson);

            String id = root.path("id").asText();
            String model = root.path("model").asText();

            List<ChatChoice> choices = new ArrayList<>();
            for (JsonNode choiceNode : root.path("choices")) {
                int index = choiceNode.path("index").asInt();
                String finishReason = choiceNode.path("finish_reason").asText(null);

                JsonNode msgNode = choiceNode.path("message");
                String content = extractContent(msgNode);
                List<ToolCall> toolCalls = parseToolCalls(msgNode);

                AssistantMessage message;
                if (toolCalls != null) {
                    message = AssistantMessage.ofToolCalls(toolCalls);
                } else {
                    message = AssistantMessage.ofText(content);
                }

                choices.add(new ChatChoice(index, message, finishReason));
            }

            JsonNode usageNode = root.path("usage");
            TokenUsage usage = new TokenUsage(
                    usageNode.path("prompt_tokens").asInt(),
                    usageNode.path("completion_tokens").asInt(),
                    usageNode.path("total_tokens").asInt()
            );

            return new ChatResponse(id, model, choices, usage);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException("Failed to parse DeepSeek response", e);
        }
    }

    /**
     * 提取消息文本内容，处理 content 为 JSON null 的情况。
     */
    private String extractContent(JsonNode msgNode) {
        if (msgNode.has("content") && !msgNode.get("content").isNull()) {
            return msgNode.get("content").asText();
        }
        return null;
    }

    /**
     * 解析工具调用列表。
     *
     * @param msgNode 消息 JSON 节点
     * @return 工具调用列表，无工具调用时返回 null
     */
    private List<ToolCall> parseToolCalls(JsonNode msgNode) {
        JsonNode tcNode = msgNode.path("tool_calls");
        if (!tcNode.isArray() || tcNode.isEmpty()) {
            return null;
        }
        List<ToolCall> toolCalls = new ArrayList<>();
        for (JsonNode tc : tcNode) {
            try {
                toolCalls.add(objectMapper.treeToValue(tc, ToolCall.class));
            } catch (Exception e) {
                throw new ApiException("Failed to parse tool_call", e);
            }
        }
        return toolCalls;
    }
}
