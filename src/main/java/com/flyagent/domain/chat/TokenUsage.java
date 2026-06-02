package com.flyagent.domain.chat;

/**
 * Token 用量统计。
 *
 * <p>记录单次 API 调用的输入、输出和总计 token 数。</p>
 *
 * @author FlyAgent Team
 * @since 1.0
 */
public class TokenUsage {

    private final int promptTokens;
    private final int completionTokens;
    private final int totalTokens;

    public TokenUsage(int promptTokens, int completionTokens, int totalTokens) {
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = totalTokens;
    }

    public int getPromptTokens() {
        return promptTokens;
    }

    public int getCompletionTokens() {
        return completionTokens;
    }

    public int getTotalTokens() {
        return totalTokens;
    }
}
