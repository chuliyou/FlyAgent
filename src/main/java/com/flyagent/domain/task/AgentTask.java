package com.flyagent.domain.task;

import com.flyagent.domain.session.SessionId;

import java.time.LocalDateTime;

/**
 * 任务聚合根。
 *
 * <p>表示用户发起的一次自然语言任务，控制最大轮次、状态流转和最终结果。
 * 一期仅使用 CREATED、RUNNING、COMPLETED、FAILED 状态，其余状态预留后续迭代。</p>
 *
 * <p>状态流转：</p>
 * <pre>
 *   CREATED -> RUNNING -> COMPLETED
 *                     \-> FAILED
 *                     \-> HIT_MAX_TURNS
 * </pre>
 *
 * @author FlyAgent Team
 * @since 1.0
 */
public class AgentTask {

    /** 任务唯一标识 */
    private final TaskId id;

    /** 所属会话 ID */
    private final SessionId sessionId;

    /** 用户原始指令 */
    private final String userInstruction;

    /** 任务状态 */
    private TaskStatus status;

    /** 最大执行轮次 */
    private final int maxTurns;

    /** 当前执行轮次 */
    private int currentTurn;

    /** 最终回答或失败原因 */
    private String finalAnswer;

    /** 创建时间 */
    private final LocalDateTime createdAt;

    /** 最近更新时间 */
    private LocalDateTime updatedAt;

    /**
     * 构造任务。
     *
     * @param sessionId 所属会话 ID
     * @param userInstruction 用户指令
     * @param maxTurns 最大轮次
     */
    public AgentTask(SessionId sessionId, String userInstruction, int maxTurns) {
        this.id = TaskId.generate();
        this.sessionId = sessionId;
        this.userInstruction = userInstruction;
        this.status = TaskStatus.CREATED;
        this.maxTurns = maxTurns;
        this.currentTurn = 0;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    /**
     * 开始执行任务。
     *
     * @throws IllegalStateException 当任务状态不是 CREATED 时
     */
    public void start() {
        if (this.status != TaskStatus.CREATED) {
            throw new IllegalStateException("Task can only start from CREATED status, current: "
                    + this.status);
        }
        this.status = TaskStatus.RUNNING;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 增加当前轮次计数。
     */
    public void increaseTurn() {
        this.currentTurn++;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 判断是否可以继续执行。
     *
     * @return true 表示可以继续
     */
    public boolean canContinue() {
        return this.status == TaskStatus.RUNNING && this.currentTurn < this.maxTurns;
    }

    /**
     * 完成任务。
     *
     * @param finalAnswer 最终回答
     */
    public void complete(String finalAnswer) {
        this.finalAnswer = finalAnswer;
        this.status = TaskStatus.COMPLETED;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 标记任务失败。
     *
     * @param reason 失败原因
     */
    public void fail(String reason) {
        this.finalAnswer = reason;
        this.status = TaskStatus.FAILED;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 标记达到最大轮次上限。
     */
    public void hitMaxTurns() {
        this.status = TaskStatus.HIT_MAX_TURNS;
        this.updatedAt = LocalDateTime.now();
    }

    public TaskId getId() {
        return id;
    }

    public SessionId getSessionId() {
        return sessionId;
    }

    public String getUserInstruction() {
        return userInstruction;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public int getMaxTurns() {
        return maxTurns;
    }

    public int getCurrentTurn() {
        return currentTurn;
    }

    public String getFinalAnswer() {
        return finalAnswer;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
