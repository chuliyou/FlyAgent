package com.flyagent.domain.agent;

import java.util.Collections;
import java.util.List;

/**
 * Agent 响应值对象（不可变）。
 *
 * <p>封装 Agent 执行的完整结果，包括最终回答和执行轨迹。
 * 通过工厂方法区分成功和失败两种语义。</p>
 *
 * @author FlyAgent Team
 * @since 1.0
 */
public class AgentResponse {

    private final boolean success;
    private final String finalAnswer;
    private final String error;
    private final List<ReActStep> steps;
    private final int totalTurns;

    private AgentResponse(boolean success, String finalAnswer, String error,
                          List<ReActStep> steps, int totalTurns) {
        this.success = success;
        this.finalAnswer = finalAnswer;
        this.error = error;
        this.steps = steps != null
                ? Collections.unmodifiableList(steps)
                : Collections.emptyList();
        this.totalTurns = totalTurns;
    }

    /**
     * 构造成功响应。
     *
     * @param finalAnswer 最终回答
     * @param steps       ReAct 执行轨迹
     * @param totalTurns  总执行轮次
     * @return AgentResponse
     */
    public static AgentResponse success(String finalAnswer, List<ReActStep> steps, int totalTurns) {
        return new AgentResponse(true, finalAnswer, null, steps, totalTurns);
    }

    /**
     * 构造失败响应。
     *
     * @param error      失败原因（人类可读）
     * @param steps      已执行的 ReAct 步骤
     * @param totalTurns 已完成轮次
     * @return AgentResponse
     */
    public static AgentResponse error(String error, List<ReActStep> steps, int totalTurns) {
        return new AgentResponse(false, null, error, steps, totalTurns);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getFinalAnswer() {
        return finalAnswer;
    }

    public String getError() {
        return error;
    }

    public List<ReActStep> getSteps() {
        return steps;
    }

    public int getTotalTurns() {
        return totalTurns;
    }
}
