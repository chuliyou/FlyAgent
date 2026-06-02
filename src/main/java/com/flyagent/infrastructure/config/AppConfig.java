package com.flyagent.infrastructure.config;

import com.flyagent.common.constant.CommonConstants;
import com.flyagent.infrastructure.deepseek.DeepSeekConfig;

/**
 * 应用配置聚合。
 *
 * <p>一期从环境变量读取 DeepSeek 相关配置，提供合理默认值。
 * 后续迭代扩展为配置文件 + 命令行参数三级配置源。</p>
 *
 * @author FlyAgent Team
 * @since 1.0
 */
public class AppConfig {

    private final DeepSeekConfig deepSeekConfig;

    /**
     * 构造应用配置（从环境变量读取）。
     */
    public AppConfig() {
        String apiKey = System.getenv(CommonConstants.ENV_API_KEY);
        String baseUrl = System.getenv().getOrDefault(
                "DEEPSEEK_BASE_URL", CommonConstants.DEFAULT_BASE_URL);
        String model = System.getenv().getOrDefault(
                "DEEPSEEK_MODEL", CommonConstants.DEFAULT_MODEL);

        this.deepSeekConfig = new DeepSeekConfig(baseUrl, apiKey, model);
    }

    public DeepSeekConfig getDeepSeekConfig() {
        return deepSeekConfig;
    }
}
