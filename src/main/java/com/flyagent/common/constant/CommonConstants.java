package com.flyagent.common.constant;

/**
 * 公共常量。
 *
 * <p>定义 DeepSeek API 默认值和环境变量名等跨模块使用的常量。</p>
 *
 * @author FlyAgent Team
 * @since 1.0
 */
public final class CommonConstants {

    private CommonConstants() {
    }

    /** DeepSeek API 默认基础地址 */
    public static final String DEFAULT_BASE_URL = "https://api.deepseek.com";

    /** Chat Completions 端点路径 */
    public static final String CHAT_ENDPOINT = "/chat/completions";

    /** 默认模型名称 */
    public static final String DEFAULT_MODEL = "deepseek-v4-pro";

    /** DeepSeek API Key 环境变量名 */
    public static final String ENV_API_KEY = "DEEPSEEK_API_KEY";
}
