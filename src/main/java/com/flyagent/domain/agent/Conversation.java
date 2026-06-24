package com.flyagent.domain.agent;

import com.flyagent.domain.message.AssistantMessage;
import com.flyagent.domain.message.Message;
import com.flyagent.domain.message.SystemMessage;
import com.flyagent.domain.message.ToolMessage;
import com.flyagent.domain.message.UserMessage;
import com.flyagent.domain.tool.ToolCall;
import com.flyagent.infrastructure.tools.TextTruncator;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 会话上下文领域聚合。
 *
 * <p>维护 Agent 与 LLM 之间的完整对话历史，包括用户消息、助手消息和工具消息。
 * 内存存储，保留最近 N 条消息并自动截断过长的工具结果。</p>
 *
 * <p>消息窗口策略：始终保留 System Message 在首位，
 * 其后保留最近 (maxMessages - 1) 条非 System 消息。</p>
 *
 * @author FlyAgent Team
 * @since 1.0
 */
public class Conversation {

    private static final int DEFAULT_MAX_MESSAGES = 20;
    private static final int DEFAULT_MAX_TOOL_RESULT_CHARS = 4000;

    private final SystemMessage systemMessage;
    private final Deque<Message> historyMessages;
    private final int maxMessages;
    private final int maxToolResultChars;

    /**
     * 构造 Conversation（使用默认窗口大小和截断阈值）。
     */
    public Conversation() {
        this(DEFAULT_MAX_MESSAGES, DEFAULT_MAX_TOOL_RESULT_CHARS);
    }

    /**
     * 构造 Conversation。
     *
     * @param maxMessages        最大保留消息数（含 System Message）
     * @param maxToolResultChars 单条工具结果最大字符数
     */
    public Conversation(int maxMessages, int maxToolResultChars) {
        this.systemMessage = null;
        this.historyMessages = new ArrayDeque<>();
        this.maxMessages = maxMessages;
        this.maxToolResultChars = maxToolResultChars;
    }

    /**
     * 添加用户消息。
     *
     * @param content 用户输入内容
     */
    public void addUserMessage(String content) {
        addMessage(new UserMessage(content));
    }

    /**
     * 添加助手纯文本消息（Final Answer 场景）。
     *
     * @param content 助手回复内容
     */
    public void addAssistantMessage(String content) {
        addMessage(AssistantMessage.ofText(content));
    }

    /**
     * 添加助手工具调用消息（Tool Calls 场景）。
     *
     * @param toolCalls 工具调用列表
     */
    public void addAssistantToolCalls(List<ToolCall> toolCalls) {
        addMessage(AssistantMessage.ofToolCalls(toolCalls));
    }

    /**
     * 添加工具执行结果消息（Observation）。
     * 会自动截断超过 maxToolResultChars 的结果。
     *
     * @param toolCallId 关联的 tool_call id
     * @param content    工具执行结果
     */
    public void addToolMessage(String toolCallId, String content) {
        String truncated = TextTruncator.truncate(content, maxToolResultChars, "Tool result");
        addMessage(new ToolMessage(toolCallId, truncated));
    }

    /**
     * 获取最近的对话消息（用于构建 ChatRequest.messages）。
     * System Message 始终在首位。
     *
     * @return 最多 maxMessages 条消息的不可变列表
     */
    public List<Message> getMessages() {
        List<Message> result = new ArrayList<>();
        // System Message always first
        if (systemMessage != null) {
            result.add(systemMessage);
        }
        // Add recent history messages (up to maxMessages - 1 for system)
        int historyLimit = systemMessage != null ? maxMessages - 1 : maxMessages;
        List<Message> history = new ArrayList<>(historyMessages);
        int startIndex = Math.max(0, history.size() - historyLimit);
        result.addAll(history.subList(startIndex, history.size()));
        return result;
    }

    /**
     * 清空所有非 System 消息。
     */
    public void clear() {
        historyMessages.clear();
    }

    /**
     * 获取当前消息总数（含 System Message）。
     *
     * @return 消息总数
     */
    public int size() {
        int count = historyMessages.size();
        if (systemMessage != null) {
            count++;
        }
        return count;
    }

    /**
     * 获取仅历史消息（不含 System Message），供 ContextBuildService 使用。
     *
     * @return 历史消息列表
     */
    List<Message> getHistoryMessages() {
        return new ArrayList<>(historyMessages);
    }

    private void addMessage(Message message) {
        historyMessages.addLast(message);
    }
}
