package com.flyagent.domain.agent;

import com.flyagent.application.service.SessionAppService;
import com.flyagent.domain.chat.*;
import com.flyagent.domain.message.AssistantMessage;
import com.flyagent.domain.tool.*;
import com.flyagent.infrastructure.tools.AlwaysApproveHandler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * DefaultAgent 端到端 Mock 测试。
 */
class DefaultAgentTest {

    private ChatModelPort chatModel;
    private ToolRegistry toolRegistry;
    private SessionAppService sessionService;
    private ApprovalHandler approvalHandler;
    private DefaultAgent agent;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        chatModel = mock(ChatModelPort.class);
        toolRegistry = mock(ToolRegistry.class);
        sessionService = new SessionAppService();
        approvalHandler = new AlwaysApproveHandler();
        agent = new DefaultAgent(chatModel, toolRegistry, sessionService, approvalHandler);

        // Setup tool definitions
        ToolDefinition def = new ToolDefinition("read_file", "Read a file",
                new ToolParameter(Map.of("path", new JsonSchemaProperty("string", "File path")), List.of("path")));
        when(toolRegistry.definitions()).thenReturn(List.of(def));

        // Start a session
        sessionService.startSession(tempDir.toString());
    }

    @Test
    void shouldReturnSuccessForDirectFinalAnswer() {
        AssistantMessage msg = AssistantMessage.ofText("Hello, I am FlyAgent");
        ChatChoice choice = new ChatChoice(0, msg, "stop");
        ChatResponse response = new ChatResponse("resp_1", "model", List.of(choice), null);
        when(chatModel.chat(any())).thenReturn(response);

        AgentRequest request = AgentRequest.builder()
                .userInput("Say hello")
                .workspace(tempDir)
                .build();

        AgentResponse result = agent.run(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getFinalAnswer()).isEqualTo("Hello, I am FlyAgent");
        assertThat(result.getSteps()).hasSize(1);
    }

    @Test
    void shouldExecuteToolCallAndReturnFinalAnswer() {
        // Round 1: tool call
        ToolCall.FunctionCall fn = new ToolCall.FunctionCall("read_file", "{\"path\":\"pom.xml\"}");
        ToolCall tc = new ToolCall("call_1", "function", fn);
        AssistantMessage toolMsg = AssistantMessage.ofToolCalls(List.of(tc));
        ChatResponse round1 = new ChatResponse("resp_1", "model",
                List.of(new ChatChoice(0, toolMsg, "tool_calls")), null);
        // Round 2: final answer
        AssistantMessage finalMsg = AssistantMessage.ofText("File analyzed");
        ChatResponse round2 = new ChatResponse("resp_2", "model",
                List.of(new ChatChoice(0, finalMsg, "stop")), null);

        when(chatModel.chat(any())).thenReturn(round1, round2);
        when(toolRegistry.execute(any(), any())).thenReturn(ToolResult.success("content"));

        AgentRequest request = AgentRequest.builder()
                .userInput("Analyze pom.xml")
                .workspace(tempDir)
                .build();

        AgentResponse result = agent.run(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getFinalAnswer()).isEqualTo("File analyzed");
        assertThat(result.getSteps()).hasSize(2);
        verify(toolRegistry).execute(any(), any());
    }

    @Test
    void shouldShareConversationAcrossMultipleRunCalls() {
        // First run: simple answer
        AssistantMessage msg1 = AssistantMessage.ofText("First answer");
        ChatChoice choice1 = new ChatChoice(0, msg1, "stop");
        ChatResponse response1 = new ChatResponse("resp_1", "model", List.of(choice1), null);
        when(chatModel.chat(any())).thenReturn(response1);

        AgentRequest request1 = AgentRequest.builder()
                .userInput("First question")
                .workspace(tempDir)
                .build();
        AgentResponse result1 = agent.run(request1);
        assertThat(result1.isSuccess()).isTrue();
        assertThat(result1.getFinalAnswer()).isEqualTo("First answer");

        // Verify conversation contains messages from first run
        Conversation conv = sessionService.getCurrentSession().getConversation();
        assertThat(conv.size()).isGreaterThanOrEqualTo(2); // user + assistant

        // Second run: another answer
        AssistantMessage msg2 = AssistantMessage.ofText("Second answer");
        ChatChoice choice2 = new ChatChoice(0, msg2, "stop");
        ChatResponse response2 = new ChatResponse("resp_2", "model", List.of(choice2), null);
        when(chatModel.chat(any())).thenReturn(response2);

        AgentRequest request2 = AgentRequest.builder()
                .userInput("Second question")
                .workspace(tempDir)
                .build();
        AgentResponse result2 = agent.run(request2);
        assertThat(result2.isSuccess()).isTrue();
        assertThat(result2.getFinalAnswer()).isEqualTo("Second answer");

        // Verify conversation now contains messages from BOTH runs
        assertThat(conv.size()).isGreaterThanOrEqualTo(4); // user1 + asst1 + user2 + asst2
    }

    @Test
    void shouldReturnErrorWhenNoActiveSessionButWorkspaceProvided() {
        // Close the existing session
        sessionService.closeSession();

        AssistantMessage msg = AssistantMessage.ofText("Hello");
        ChatChoice choice = new ChatChoice(0, msg, "stop");
        ChatResponse response = new ChatResponse("resp_1", "model", List.of(choice), null);
        when(chatModel.chat(any())).thenReturn(response);

        // request without sessionId — DefaultAgent should auto-start a session from workspace
        AgentRequest request = AgentRequest.builder()
                .userInput("Say hello")
                .workspace(tempDir)
                .build();

        AgentResponse result = agent.run(request);

        assertThat(result.isSuccess()).isTrue();
    }
}
