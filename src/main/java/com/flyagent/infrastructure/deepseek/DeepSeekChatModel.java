package com.flyagent.infrastructure.deepseek;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flyagent.common.exception.ConfigException;
import com.flyagent.domain.chat.ChatModelPort;
import com.flyagent.domain.chat.ChatRequest;
import com.flyagent.domain.chat.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DeepSeek 聊天模型实现。
 *
 * <p>实现 ChatModelPort 端口，串联 API Key 校验 → HTTP 请求 → 响应解析的完整链路。
 * 是第一次开发的核心交付件。</p>
 *
 * @author FlyAgent Team
 * @since 1.0
 */
public class DeepSeekChatModel implements ChatModelPort {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekChatModel.class);

    private final DeepSeekConfig config;
    private final DeepSeekHttpClient httpClient;
    private final DeepSeekResponseParser parser;

    public DeepSeekChatModel(DeepSeekConfig config, ObjectMapper objectMapper) {
        this.config = config;
        this.httpClient = new DeepSeekHttpClient(config, objectMapper);
        this.parser = new DeepSeekResponseParser(objectMapper);
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        // 1. 前置校验
        if (config.getApiKey() == null || config.getApiKey().isBlank()) {
            throw new ConfigException(
                    "DeepSeek API Key not set. Set DEEPSEEK_API_KEY environment variable.");
        }

        // 2. 模型补全（request 未设置则使用 config 默认值）
        ChatRequest resolvedRequest = resolveModel(request);

        // 3. HTTP 请求
        log.debug("Sending chat request: model={}, messages={}, tools={}",
                resolvedRequest.getModel(),
                resolvedRequest.getMessages().size(),
                resolvedRequest.getTools() != null ? resolvedRequest.getTools().size() : 0);

        long start = System.currentTimeMillis();
        String responseJson = httpClient.postChatRequest(resolvedRequest);
        long latencyMs = System.currentTimeMillis() - start;

        // 4. 解析响应
        ChatResponse response = parser.parse(responseJson);

        // 5. 日志记录
        if (response.getUsage() != null) {
            log.info("Chat completed: model={}, latencyMs={}, "
                            + "promptTokens={}, completionTokens={}, totalTokens={}",
                    response.getModel(), latencyMs,
                    response.getUsage().getPromptTokens(),
                    response.getUsage().getCompletionTokens(),
                    response.getUsage().getTotalTokens());
        }

        return response;
    }

    private ChatRequest resolveModel(ChatRequest request) {
        if (request.getModel() != null && !request.getModel().isEmpty()) {
            return request;
        }
        return ChatRequest.builder()
                .model(config.getDefaultModel())
                .messages(request.getMessages())
                .tools(request.getTools())
                .thinking(request.isThinking())
                .reasoningEffort(request.getReasoningEffort())
                .stream(request.isStream())
                .build();
    }
}
