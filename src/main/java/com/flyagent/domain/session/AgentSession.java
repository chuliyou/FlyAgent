package com.flyagent.domain.session;

import com.flyagent.domain.agent.Conversation;

import java.time.LocalDateTime;

/**
 * Agent 会话聚合根。
 *
 * <p>表示一次 CLI 交互式会话，维护 workspace、会话状态、对话历史和生命周期时间戳。
 * 所有对会话状态的变更必须通过本聚合根的行为方法执行。</p>
 *
 * @author FlyAgent Team
 * @since 1.0
 */
public class AgentSession {

    private static final int DEFAULT_MAX_MESSAGES = 20;
    private static final int DEFAULT_MAX_TOOL_RESULT_CHARS = 4000;

    /** 会话唯一标识 */
    private final SessionId id;

    /** 工作目录 */
    private final Workspace workspace;

    /** 会话状态 */
    private SessionStatus status;

    /** 对话历史（session 级别生命周期） */
    private final Conversation conversation;

    /** 创建时间 */
    private final LocalDateTime createdAt;

    /** 最近更新时间 */
    private LocalDateTime updatedAt;

    /** 关闭时间 */
    private LocalDateTime closedAt;

    public AgentSession(Workspace workspace) {
        this.id = SessionId.generate();
        this.workspace = workspace;
        this.status = SessionStatus.ACTIVE;
        this.conversation = new Conversation(DEFAULT_MAX_MESSAGES, DEFAULT_MAX_TOOL_RESULT_CHARS);
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    /**
     * 激活会话（从非关闭状态恢复为活跃）。
     *
     * @throws IllegalStateException 当会话已关闭时
     */
    public void activate() {
        if (this.status == SessionStatus.CLOSED) {
            throw new IllegalStateException("Cannot activate a closed session");
        }
        this.status = SessionStatus.ACTIVE;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 关闭会话。
     */
    public void close() {
        this.status = SessionStatus.CLOSED;
        this.closedAt = LocalDateTime.now();
        this.updatedAt = this.closedAt;
    }

    /**
     * 判断会话是否处于活跃状态。
     *
     * @return true 表示活跃
     */
    public boolean isActive() {
        return this.status == SessionStatus.ACTIVE;
    }

    public SessionId getId() {
        return id;
    }

    public Workspace getWorkspace() {
        return workspace;
    }

    public SessionStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public LocalDateTime getClosedAt() {
        return closedAt;
    }

    /**
     * 获取当前会话的对话历史。
     *
     * @return Conversation 实例，不为 null
     */
    public Conversation getConversation() {
        return conversation;
    }

    /**
     * 清空对话历史（不含 System Message）。
     * 更新 updatedAt 时间戳。
     */
    public void clearConversation() {
        this.conversation.clear();
        this.updatedAt = LocalDateTime.now();
    }
}
