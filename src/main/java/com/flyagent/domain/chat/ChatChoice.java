package com.flyagent.domain.chat;

import com.flyagent.domain.message.AssistantMessage;

/**
 * 单条选择结果。
 *
 * <p>包含模型回复消息和结束原因。finishReason 是判断后续动作的关键依据。</p>
 *
 * @author FlyAgent Team
 * @since 1.0
 */
public class ChatChoice {

    /** 索引序号 */
    private final int index;

    /** 模型回复消息 */
    private final AssistantMessage message;

    /** 结束原因：stop / tool_calls / length */
    private final String finishReason;

    public ChatChoice(int index, AssistantMessage message, String finishReason) {
        this.index = index;
        this.message = message;
        this.finishReason = finishReason;
    }

    public int getIndex() {
        return index;
    }

    public AssistantMessage getMessage() {
        return message;
    }

    public String getFinishReason() {
        return finishReason;
    }
}
