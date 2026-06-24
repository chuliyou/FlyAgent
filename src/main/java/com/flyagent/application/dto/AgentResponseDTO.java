package com.flyagent.application.dto;

import com.flyagent.domain.agent.AgentResponse;
import com.flyagent.domain.agent.ReActStep;
import com.flyagent.domain.chat.TokenUsage;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Agent 响应 DTO。
 *
 * <p>封装应用层返回给 CLI 层的响应数据，屏蔽领域模型细节。
 * 支持 ReAct 多轮执行轨迹的展示。</p>
 *
 * @author FlyAgent Team
 * @since 1.0
 */
public class AgentResponseDTO {

    /** 是否成功 */
    private final boolean success;

    /** 文本内容 */
    private final String content;

    /** 是否包含工具调用 */
    private final boolean hasToolCalls;

    /** Token 用量 */
    private final TokenUsage tokenUsage;

    /** 错误信息 */
    private final String errorMessage;

    /** ReAct 执行轨迹（精简版） */
    private final List<ReActStepDTO> steps;

    /** 总轮次数 */
    private final int totalTurns;

    private AgentResponseDTO(boolean success, String content, boolean hasToolCalls,
                              TokenUsage tokenUsage, String errorMessage,
                              List<ReActStepDTO> steps, int totalTurns) {
        this.success = success;
        this.content = content;
        this.hasToolCalls = hasToolCalls;
        this.tokenUsage = tokenUsage;
        this.errorMessage = errorMessage;
        this.steps = steps != null ? steps : Collections.emptyList();
        this.totalTurns = totalTurns;
    }

    /**
     * 构造成功响应。
     */
    public static AgentResponseDTO success(String content, boolean hasToolCalls,
                                            TokenUsage usage) {
        return new AgentResponseDTO(true, content, hasToolCalls, usage, null,
                Collections.emptyList(), 1);
    }

    /**
     * 构造错误响应。
     */
    public static AgentResponseDTO error(String errorMessage) {
        return new AgentResponseDTO(false, null, false, null, errorMessage,
                Collections.emptyList(), 0);
    }

    /**
     * 从领域 AgentResponse 转换为 DTO。
     *
     * @param response 领域响应对象
     * @return DTO
     */
    public static AgentResponseDTO from(AgentResponse response) {
        List<ReActStepDTO> stepDTOs = response.getSteps().stream()
                .map(ReActStepDTO::from)
                .collect(Collectors.toList());

        return new AgentResponseDTO(
                response.isSuccess(),
                response.getFinalAnswer(),
                !stepDTOs.isEmpty(),
                null, // tokenUsage not tracked per-response currently
                response.getError(),
                stepDTOs,
                response.getTotalTurns()
        );
    }

    public boolean isSuccess() {
        return success;
    }

    public String getContent() {
        return content;
    }

    public boolean isHasToolCalls() {
        return hasToolCalls;
    }

    public TokenUsage getTokenUsage() {
        return tokenUsage;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public List<ReActStepDTO> getSteps() {
        return steps;
    }

    public int getTotalTurns() {
        return totalTurns;
    }

    /**
     * ReAct 执行步骤 DTO（精简版，供 CLI 展示）。
     */
    public static class ReActStepDTO {
        private final int turn;
        private final String thought;
        private final String actionName;
        private final Map<String, Object> actionArguments;
        private final String observation;
        private final String finalAnswer;
        private final boolean success;
        private final String error;

        ReActStepDTO(int turn, String thought, String actionName,
                     Map<String, Object> actionArguments, String observation,
                     String finalAnswer, boolean success, String error) {
            this.turn = turn;
            this.thought = thought;
            this.actionName = actionName;
            this.actionArguments = actionArguments;
            this.observation = observation;
            this.finalAnswer = finalAnswer;
            this.success = success;
            this.error = error;
        }

        static ReActStepDTO from(ReActStep step) {
            return new ReActStepDTO(
                    step.getTurn(),
                    step.getThought(),
                    step.getActionName(),
                    step.getActionArguments(),
                    step.getObservation(),
                    step.getFinalAnswer(),
                    step.isSuccess(),
                    step.getError()
            );
        }

        public int getTurn() { return turn; }
        public String getThought() { return thought; }
        public String getActionName() { return actionName; }
        public Map<String, Object> getActionArguments() { return actionArguments; }
        public String getObservation() { return observation; }
        public String getFinalAnswer() { return finalAnswer; }
        public boolean isSuccess() { return success; }
        public String getError() { return error; }
    }
}
