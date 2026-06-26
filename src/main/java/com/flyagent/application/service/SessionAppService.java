package com.flyagent.application.service;

import com.flyagent.domain.agent.Conversation;
import com.flyagent.domain.agent.ContextBuildService;
import com.flyagent.domain.chat.ChatModelPort;
import com.flyagent.domain.chat.ChatRequest;
import com.flyagent.domain.chat.ChatResponse;
import com.flyagent.domain.message.Message;
import com.flyagent.domain.message.UserMessage;
import com.flyagent.domain.session.AgentSession;
import com.flyagent.domain.session.Workspace;

import java.util.Collections;
import java.util.List;

/**
 * 会话应用服务。
 *
 * <p>负责会话生命周期管理：创建、关闭、状态查询。一期在内存中维护，不持久化。</p>
 *
 * @author FlyAgent Team
 * @since 1.0
 */
public class SessionAppService {

    private static final String COMPACT_SYSTEM_PROMPT = ""
            + "You are a conversation summarizer. Your task is to create a concise but comprehensive "
            + "summary of the conversation below. Preserve all critical information:\n"
            + "- Key decisions made and their rationale\n"
            + "- File paths, code snippets, and technical details\n"
            + "- Unfinished tasks or pending action items\n"
            + "- User's stated preferences and constraints\n"
            + "- Error messages and debugging context\n\n"
            + "Write the summary in a clear narrative form. The summary will replace the original "
            + "conversation history to save context space for future interactions.";

    /** 当前活跃会话 */
    private AgentSession currentSession;

    /** LLM 调用端口（用于 /compact 摘要生成） */
    private final ChatModelPort chatModel;

    public SessionAppService() {
        this.chatModel = null;
    }

    /**
     * 构造 SessionAppService（带 ChatModelPort 以支持 /compact）。
     *
     * @param chatModel LLM 调用端口
     */
    public SessionAppService(ChatModelPort chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * 启动新会话。
     *
     * @param workspacePath 工作目录路径
     * @return 新创建的会话
     */
    public AgentSession startSession(String workspacePath) {
        Workspace workspace = new Workspace(workspacePath);
        this.currentSession = new AgentSession(workspace);
        return this.currentSession;
    }

    /**
     * 获取当前会话。
     *
     * @return 当前会话，未启动时返回 null
     */
    public AgentSession getCurrentSession() {
        return currentSession;
    }

    /**
     * 关闭当前会话。
     */
    public void closeSession() {
        if (currentSession != null) {
            currentSession.close();
        }
    }

    /**
     * 判断是否有活跃会话。
     *
     * @return true 表示存在活跃会话
     */
    public boolean hasActiveSession() {
        return currentSession != null && currentSession.isActive();
    }

    /**
     * 清空当前会话的对话历史。
     */
    public void clearConversation() {
        if (currentSession != null) {
            currentSession.clearConversation();
        }
    }

    /**
     * 压缩当前会话的对话历史：调用 LLM 生成摘要后替换原始消息。
     *
     * @param workspacePath 工作目录路径（用于上下文中日期/路径信息）
     * @return 压缩结果信息
     * @throws IllegalStateException 当 chatModel 未注入时
     */
    public String compactConversation(String workspacePath) {
        if (currentSession == null || currentSession.getConversation() == null) {
            return "No active conversation to compact.";
        }

        Conversation conversation = currentSession.getConversation();
        List<Message> messages = conversation.getMessages();
        if (messages.isEmpty()) {
            return "Nothing to compact — conversation is already empty.";
        }

        if (chatModel == null) {
            throw new IllegalStateException("ChatModelPort not configured for compact operation");
        }

        int originalMessageCount = conversation.size();

        // Build a summarization request: first message is the compact instruction,
        // followed by all current messages, ending with a summarization request.
        ContextBuildService contextBuilder = new ContextBuildService();
        Conversation tempConv = new Conversation();
        tempConv.addUserMessage(COMPACT_SYSTEM_PROMPT);
        for (Message msg : messages) {
            // Skip existing system message, copy everything else
            if (msg instanceof com.flyagent.domain.message.UserMessage) {
                tempConv.addUserMessage(
                        ((com.flyagent.domain.message.UserMessage) msg).getContent());
            } else if (msg instanceof com.flyagent.domain.message.AssistantMessage) {
                com.flyagent.domain.message.AssistantMessage am =
                        (com.flyagent.domain.message.AssistantMessage) msg;
                if (am.hasToolCalls()) {
                    tempConv.addAssistantToolCalls(am.getToolCalls());
                } else if (am.hasContent()) {
                    tempConv.addAssistantMessage(am.getContent());
                }
            } else if (msg instanceof com.flyagent.domain.message.ToolMessage) {
                com.flyagent.domain.message.ToolMessage tm =
                        (com.flyagent.domain.message.ToolMessage) msg;
                tempConv.addToolMessage(tm.getToolCallId(), tm.getContent());
            }
        }
        tempConv.addUserMessage("Please summarize the conversation above.");

        java.nio.file.Path wsPath = java.nio.file.Path.of(workspacePath);
        ChatRequest request = contextBuilder.build(
                tempConv, Collections.emptyList(), wsPath);

        ChatResponse response = chatModel.chat(request);
        String summary;
        if (response != null && response.firstChoice() != null
                && response.firstChoice().getMessage() != null) {
            summary = response.firstChoice().getMessage().getContent();
            if (summary == null || summary.isBlank()) {
                return "Compact failed: LLM returned empty summary.";
            }
        } else {
            return "Compact failed: LLM returned no response.";
        }

        conversation.compact(summary);
        return "Compacted " + originalMessageCount + " messages into summary ("
                + summary.length() + " chars).";
    }
}
