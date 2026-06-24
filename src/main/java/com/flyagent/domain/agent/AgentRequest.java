package com.flyagent.domain.agent;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Agent 请求值对象（不可变）。
 *
 * <p>封装一次 Agent 执行所需的全部输入参数。
 * 通过 Builder 模式构造，build() 时进行输入校验。</p>
 *
 * @author FlyAgent Team
 * @since 1.0
 */
public class AgentRequest {

    private static final int DEFAULT_MAX_TURNS = 8;
    private static final int MIN_MAX_TURNS = 1;
    private static final int MAX_MAX_TURNS = 100;

    private final String sessionId;
    private final String userInput;
    private final Path workspace;
    private final int maxTurns;

    private AgentRequest(Builder builder) {
        this.sessionId = builder.sessionId;
        this.userInput = builder.userInput;
        this.workspace = builder.workspace;
        this.maxTurns = builder.maxTurns;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getUserInput() {
        return userInput;
    }

    public Path getWorkspace() {
        return workspace;
    }

    public int getMaxTurns() {
        return maxTurns;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * AgentRequest 建造器。
     */
    public static class Builder {
        private String sessionId;
        private String userInput;
        private Path workspace;
        private int maxTurns = DEFAULT_MAX_TURNS;

        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder userInput(String userInput) {
            this.userInput = userInput;
            return this;
        }

        public Builder workspace(Path workspace) {
            this.workspace = workspace;
            return this;
        }

        public Builder maxTurns(int maxTurns) {
            this.maxTurns = maxTurns;
            return this;
        }

        /**
         * 构建 AgentRequest，进行输入校验。
         *
         * @return AgentRequest 实例
         * @throws IllegalArgumentException 当 userInput 为空或 workspace 无效时
         */
        public AgentRequest build() {
            if (userInput == null || userInput.isBlank()) {
                throw new IllegalArgumentException("userInput must not be empty");
            }
            if (workspace == null) {
                throw new IllegalArgumentException("workspace must not be null");
            }
            if (!Files.isDirectory(workspace)) {
                throw new IllegalArgumentException(
                        "workspace does not exist or is not a directory: " + workspace);
            }
            // Clamp maxTurns to valid range
            if (maxTurns < MIN_MAX_TURNS) {
                maxTurns = MIN_MAX_TURNS;
            } else if (maxTurns > MAX_MAX_TURNS) {
                maxTurns = MAX_MAX_TURNS;
            }
            return new AgentRequest(this);
        }
    }
}
