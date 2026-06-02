package com.flyagent.infrastructure.deepseek;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flyagent.common.exception.ApiException;
import com.flyagent.domain.chat.ChatRequest;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * DeepSeek HTTP 客户端。
 *
 * <p>基于 OkHttp 发送同步 POST 请求到 /chat/completions，返回原始 JSON 字符串。
 * 负责将领域 ChatRequest 序列化为 API 请求体。</p>
 *
 * @author FlyAgent Team
 * @since 1.0
 */
public class DeepSeekHttpClient {

    private static final MediaType JSON_MEDIA_TYPE =
            MediaType.parse("application/json; charset=utf-8");

    private final DeepSeekConfig config;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public DeepSeekHttpClient(DeepSeekConfig config, ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofMillis(config.getConnectTimeoutMs()))
                .readTimeout(Duration.ofMillis(config.getReadTimeoutMs()))
                .build();
    }

    /**
     * 发送聊天请求。
     *
     * @param request 聊天请求
     * @return API 原始响应 JSON 字符串
     * @throws ApiException 当请求失败时
     */
    public String postChatRequest(ChatRequest request) {
        Map<String, Object> body = buildRequestBody(request);
        String json;
        try {
            json = objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new ApiException("Failed to serialize ChatRequest", e);
        }

        Request httpRequest = new Request.Builder()
                .url(config.getChatEndpoint())
                .header("Authorization", "Bearer " + config.getApiKey())
                .header("Content-Type", "application/json; charset=utf-8")
                .post(RequestBody.create(json, JSON_MEDIA_TYPE))
                .build();

        try (Response response = httpClient.newCall(httpRequest).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "";
                throw new ApiException(response.code(),
                        "DeepSeek API returned " + response.code() + ": " + errorBody);
            }
            return response.body().string();
        } catch (ApiException e) {
            throw e;
        } catch (IOException e) {
            throw new ApiException("Failed to call DeepSeek API: " + e.getMessage(), e);
        }
    }

    /**
     * 构建 API 请求体。
     *
     * @param request 聊天请求
     * @return JSON Map
     */
    Map<String, Object> buildRequestBody(ChatRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", resolveModel(request));
        body.put("messages", request.getMessages());
        body.put("stream", false);

        if (request.isThinking()) {
            body.put("thinking", Map.of("type", "enabled"));
        }

        if (request.getReasoningEffort() != null && !request.getReasoningEffort().isEmpty()) {
            body.put("reasoning_effort", request.getReasoningEffort());
        }

        if (request.getTools() != null && !request.getTools().isEmpty()) {
            body.put("tools", request.getTools());
        }

        return body;
    }

    private String resolveModel(ChatRequest request) {
        String reqModel = request.getModel();
        return (reqModel != null && !reqModel.isEmpty()) ? reqModel : config.getDefaultModel();
    }
}
