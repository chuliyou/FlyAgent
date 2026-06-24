package com.flyagent.domain.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AgentRequest 单元测试。
 */
class AgentRequestTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldConstructWithValidInputs() {
        AgentRequest request = AgentRequest.builder()
                .userInput("test task")
                .workspace(tempDir)
                .build();

        assertThat(request.getUserInput()).isEqualTo("test task");
        assertThat(request.getWorkspace()).isEqualTo(tempDir);
        assertThat(request.getMaxTurns()).isEqualTo(8); // default
        assertThat(request.getSessionId()).isNull();
    }

    @Test
    void shouldThrowWhenUserInputIsNull() {
        assertThatThrownBy(() -> AgentRequest.builder()
                .userInput(null)
                .workspace(tempDir)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userInput must not be empty");
    }

    @Test
    void shouldThrowWhenUserInputIsBlank() {
        assertThatThrownBy(() -> AgentRequest.builder()
                .userInput("   ")
                .workspace(tempDir)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userInput must not be empty");
    }

    @Test
    void shouldThrowWhenWorkspaceIsNull() {
        assertThatThrownBy(() -> AgentRequest.builder()
                .userInput("test")
                .workspace(null)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("workspace must not be null");
    }

    @Test
    void shouldThrowWhenWorkspaceDoesNotExist() {
        Path nonExistent = tempDir.resolve("non-existent-dir");

        assertThatThrownBy(() -> AgentRequest.builder()
                .userInput("test")
                .workspace(nonExistent)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("workspace does not exist");
    }

    @Test
    void shouldUseDefaultMaxTurns() {
        AgentRequest request = AgentRequest.builder()
                .userInput("test")
                .workspace(tempDir)
                .build();

        assertThat(request.getMaxTurns()).isEqualTo(8);
    }

    @Test
    void shouldAcceptCustomMaxTurns() {
        AgentRequest request = AgentRequest.builder()
                .userInput("test")
                .workspace(tempDir)
                .maxTurns(15)
                .build();

        assertThat(request.getMaxTurns()).isEqualTo(15);
    }

    @Test
    void shouldAcceptSessionId() {
        AgentRequest request = AgentRequest.builder()
                .sessionId("ses_abc123")
                .userInput("test")
                .workspace(tempDir)
                .build();

        assertThat(request.getSessionId()).isEqualTo("ses_abc123");
    }
}
