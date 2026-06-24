package com.flyagent.domain.agent;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ReAct 执行步骤值对象（不可变）。
 *
 * <p>记录单轮 ReAct 循环的完整信息：Thought → Action → Observation，
 * 或 Final Answer（无需工具调用时的最终回复）。
 * 支持三种形态：Tool Call、Final Answer、Error。</p>
 *
 * @author FlyAgent Team
 * @since 1.0
 */
public class ReActStep {

    private final int turn;
    private final String thought;
    private final String actionName;
    private final Map<String, Object> actionArguments;
    private final String observation;
    private final String finalAnswer;
    private final boolean success;
    private final String error;

    private ReActStep(Builder builder) {
        this.turn = builder.turn;
        this.thought = builder.thought;
        this.actionName = builder.actionName;
        this.actionArguments = builder.actionArguments != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(builder.actionArguments))
                : Collections.emptyMap();
        this.observation = builder.observation;
        this.finalAnswer = builder.finalAnswer;
        this.success = builder.success;
        this.error = builder.error;
    }

    public int getTurn() {
        return turn;
    }

    public String getThought() {
        return thought;
    }

    public String getActionName() {
        return actionName;
    }

    public Map<String, Object> getActionArguments() {
        return actionArguments;
    }

    public String getObservation() {
        return observation;
    }

    public String getFinalAnswer() {
        return finalAnswer;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getError() {
        return error;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * ReActStep 建造器。
     */
    public static class Builder {
        private int turn;
        private String thought;
        private String actionName;
        private Map<String, Object> actionArguments;
        private String observation;
        private String finalAnswer;
        private boolean success = true;
        private String error;

        public Builder turn(int turn) {
            this.turn = turn;
            return this;
        }

        public Builder thought(String thought) {
            this.thought = thought;
            return this;
        }

        public Builder actionName(String actionName) {
            this.actionName = actionName;
            return this;
        }

        public Builder actionArguments(Map<String, Object> actionArguments) {
            this.actionArguments = actionArguments;
            return this;
        }

        public Builder observation(String observation) {
            this.observation = observation;
            return this;
        }

        public Builder finalAnswer(String finalAnswer) {
            this.finalAnswer = finalAnswer;
            return this;
        }

        public Builder success(boolean success) {
            this.success = success;
            return this;
        }

        public Builder error(String error) {
            this.error = error;
            return this;
        }

        public ReActStep build() {
            return new ReActStep(this);
        }
    }
}
