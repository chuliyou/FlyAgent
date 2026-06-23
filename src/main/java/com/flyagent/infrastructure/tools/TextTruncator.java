package com.flyagent.infrastructure.tools;

/**
 * 文本截断工具类。
 * <p>
 * 用于将超长文本截断到指定长度，并附加截断标记。
 *
 * @author FlyAgent Team
 * @since 1.0
 */
public final class TextTruncator {

    private TextTruncator() {}

    /**
     * 截断文本。
     *
     * @param text     原始文本
     * @param maxChars 最大字符数
     * @param label    内容标签（如 "File: pom.xml"）
     * @return 截断后的文本，未超限则原文返回
     */
    public static String truncate(String text, int maxChars, String label) {
        if (text == null) return "";
        if (text.length() <= maxChars) return text;
        return text.substring(0, maxChars) + "\n\n[... truncated at " + maxChars
                + " characters, total " + text.length() + " characters]";
    }
}
