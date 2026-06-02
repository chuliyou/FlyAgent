package com.flyagent.infrastructure.deepseek;

import com.flyagent.common.constant.CommonConstants;

/**
 * DeepSeek API 配置。
 *
 * <p>封装 API 连接参数：地址、密钥、模型名、超时时间等。</p>
 *
 * @author FlyAgent Team
 * @since 1.0
 */
public class DeepSeekConfig {

    /** API 基础地址 */
    private final String baseUrl;

    /** API 密钥 */
    private final String apiKey;

    /** 默认模型 */
    private final String defaultModel;

    /** 连接超时（毫秒） */
    private final int connectTimeoutMs;

    /** 读取超时（毫秒） */
    private final int readTimeoutMs;

    /**
     * 构造 DeepSeek 配置（使用默认超时值）。
     *
     * @param baseUrl API 基础地址
     * @param apiKey API 密钥
     * @param defaultModel 默认模型名
     */
    public DeepSeekConfig(String baseUrl, String apiKey, String defaultModel) {
        this(baseUrl, apiKey, defaultModel, 30_000, 120_000);
    }

    /**
     * 构造 DeepSeek 配置（完整参数）。
     *
     * @param baseUrl API 基础地址
     * @param apiKey API 密钥
     * @param defaultModel 默认模型名
     * @param connectTimeoutMs 连接超时
     * @param readTimeoutMs 读取超时
     */
    public DeepSeekConfig(String baseUrl, String apiKey, String defaultModel,
                           int connectTimeoutMs, int readTimeoutMs) {
        this.baseUrl = baseUrl != null ? baseUrl : CommonConstants.DEFAULT_BASE_URL;
        this.apiKey = apiKey;
        this.defaultModel = defaultModel != null ? defaultModel : CommonConstants.DEFAULT_MODEL;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getDefaultModel() {
        return defaultModel;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    /**
     * 获取 Chat Completions 完整端点地址。
     *
     * @return 完整 URL
     */
    public String getChatEndpoint() {
        return baseUrl + CommonConstants.CHAT_ENDPOINT;
    }
}
