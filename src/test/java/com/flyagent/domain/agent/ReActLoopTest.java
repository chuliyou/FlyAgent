package com.flyagent.domain.agent;

import com.flyagent.common.exception.ApiException;
import com.flyagent.domain.chat.*;
import com.flyagent.domain.message.AssistantMessage;
import com.flyagent.domain.task.AgentTask;
import com.flyagent.domain.task.TaskStatus;
import com.flyagent.domain.tool.*;
import com.flyagent.domain.session.SessionId;

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
 * ReActLoop 组件测试（使用 Mock）。
 */
class ReActLoopTest {

    private ChatModelPort chatModel;
    private ToolRegistry toolRegistry;
    private ReActLoop reActLoop;
    private Conversation conversation;
    private ContextBuildService contextBuilder;
    private ToolExecutionContext toolContext;
    private AgentTask task;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        chatModel = mock(ChatModelPort.class);
        toolRegistry = mock(ToolRegistry.class);
        reActLoop = new ReActLoop(chatModel, toolRegistry);
        conversation = new Conversation(20, 4000);
        contextBuilder = new ContextBuildService();
        toolContext = ToolExecutionContext.builder(tempDir).build();

        // Mock tool definitions
        ToolDefinition def1 = new ToolDefinition("read_file", "Read a file",
                new ToolParameter(Map.of("path", new JsonSchemaProperty("string", "File path")), List.of("path")));
        ToolDefinition def2 = new ToolDefinition("list_files", "List files",
                new ToolParameter(Map.of("path", new JsonSchemaProperty("string", "Directory path")), List.of()));

        when(toolRegistry.definitions()).thenReturn(List.of(def1, def2));
    }

    private AgentRequest buildRequest(int maxTurns) {
        return AgentRequest.builder()
                .userInput("test task")
                .workspace(tempDir)
                .maxTurns(maxTurns)
                .build();
    }

    private AgentTask buildTask(int maxTurns) {
        return new AgentTask(SessionId.generate(), "test task", maxTurns);
    }

    // --- Final Answer ---

    @Test
    void shouldHandleFinalAnswerDirectly() {
        // Mock: LLM returns content, no tool calls
        AssistantMessage msg = AssistantMessage.ofText("The answer is 42");
        ChatChoice choice = new ChatChoice(0, msg, "stop");
        ChatResponse response = new ChatResponse("resp_1", "model", List.of(choice), null);
        when(chatModel.chat(any())).thenReturn(response);

        AgentRequest request = buildRequest(8);
        task = buildTask(8);

        AgentResponse result = reActLoop.execute(request, toolContext, contextBuilder, conversation, task);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getFinalAnswer()).isEqualTo("The answer is 42");
        assertThat(result.getSteps()).hasSize(1);
        assertThat(result.getSteps().get(0).getFinalAnswer()).isEqualTo("The answer is 42");
        assertThat(result.getTotalTurns()).isEqualTo(1);
        assertThat(task.getStatus()).isEqualTo(TaskStatus.COMPLETED);
    }

    // --- Single Tool Call + Final ---

    @Test
    void shouldHandleSingleToolCallThenFinalAnswer() {
        // Round 1: LLM returns tool call
        ToolCall.FunctionCall fn1 = new ToolCall.FunctionCall("read_file", "{\"path\":\"pom.xml\"}");
        ToolCall toolCall = new ToolCall("call_1", "function", fn1);
        AssistantMessage toolMsg = AssistantMessage.ofToolCalls(List.of(toolCall));
        ChatResponse round1 = new ChatResponse("resp_1", "model",
                List.of(new ChatChoice(0, toolMsg, "tool_calls")), null);

        // Round 2: LLM returns final answer
        AssistantMessage finalMsg = AssistantMessage.ofText("File contents analyzed");
        ChatResponse round2 = new ChatResponse("resp_2", "model",
                List.of(new ChatChoice(0, finalMsg, "stop")), null);

        when(chatModel.chat(any())).thenReturn(round1, round2);
        when(toolRegistry.execute(any(), any()))
                .thenReturn(ToolResult.success("pom.xml content here"));

        AgentRequest request = buildRequest(8);
        task = buildTask(8);

        AgentResponse result = reActLoop.execute(request, toolContext, contextBuilder, conversation, task);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getFinalAnswer()).isEqualTo("File contents analyzed");
        assertThat(result.getSteps()).hasSize(2);
        assertThat(result.getTotalTurns()).isEqualTo(2);

        // First step: tool call
        ReActStep step1 = result.getSteps().get(0);
        assertThat(step1.getActionName()).isEqualTo("read_file");
        assertThat(step1.getObservation()).contains("pom.xml content here");
        assertThat(step1.isSuccess()).isTrue();

        // Second step: final answer
        ReActStep step2 = result.getSteps().get(1);
        assertThat(step2.getFinalAnswer()).isEqualTo("File contents analyzed");

        // Verify tool was executed
        verify(toolRegistry).execute(any(), any());
    }

    // --- Multi Tool Call ---

    @Test
    void shouldExecuteMultipleToolCallsSequentially() {
        // Round 1: LLM returns 2 tool calls
        ToolCall.FunctionCall fn1 = new ToolCall.FunctionCall("read_file", "{\"path\":\"pom.xml\"}");
        ToolCall.FunctionCall fn2 = new ToolCall.FunctionCall("list_files", "{\"path\":\".\"}");
        ToolCall tc1 = new ToolCall("call_1", "function", fn1);
        ToolCall tc2 = new ToolCall("call_2", "function", fn2);
        AssistantMessage toolMsg = AssistantMessage.ofToolCalls(List.of(tc1, tc2));
        ChatResponse round1 = new ChatResponse("resp_1", "model",
                List.of(new ChatChoice(0, toolMsg, "tool_calls")), null);

        // Round 2: final answer
        AssistantMessage finalMsg = AssistantMessage.ofText("Done");
        ChatResponse round2 = new ChatResponse("resp_2", "model",
                List.of(new ChatChoice(0, finalMsg, "stop")), null);

        when(chatModel.chat(any())).thenReturn(round1, round2);
        when(toolRegistry.execute(any(), any()))
                .thenReturn(ToolResult.success("result"));

        AgentRequest request = buildRequest(8);
        task = buildTask(8);

        AgentResponse result = reActLoop.execute(request, toolContext, contextBuilder, conversation, task);

        assertThat(result.isSuccess()).isTrue();
        // Round 1 has 2 tool executions, round 2 has final answer = 3 steps
        assertThat(result.getSteps()).hasSize(3);
        verify(toolRegistry, times(2)).execute(any(), any());
    }

    // --- Tool Execution Failure ---

    @Test
    void shouldHandleToolExecutionFailureGracefully() {
        ToolCall.FunctionCall fn = new ToolCall.FunctionCall("read_file", "{\"path\":\"missing.txt\"}");
        ToolCall tc = new ToolCall("call_1", "function", fn);
        AssistantMessage toolMsg = AssistantMessage.ofToolCalls(List.of(tc));
        ChatResponse round1 = new ChatResponse("resp_1", "model",
                List.of(new ChatChoice(0, toolMsg, "tool_calls")), null);

        AssistantMessage finalMsg = AssistantMessage.ofText("File not found, cannot proceed");
        ChatResponse round2 = new ChatResponse("resp_2", "model",
                List.of(new ChatChoice(0, finalMsg, "stop")), null);

        when(chatModel.chat(any())).thenReturn(round1, round2);
        when(toolRegistry.execute(any(), any()))
                .thenReturn(ToolResult.error("File not found: missing.txt"));

        AgentRequest request = buildRequest(8);
        task = buildTask(8);

        AgentResponse result = reActLoop.execute(request, toolContext, contextBuilder, conversation, task);

        assertThat(result.isSuccess()).isTrue(); // Agent continues after tool failure, then gets final answer
        ReActStep step = result.getSteps().get(0);
        assertThat(step.isSuccess()).isFalse();
        assertThat(step.getError()).isEqualTo("File not found: missing.txt");
        assertThat(step.getObservation()).contains("Error");
    }

    // --- Unknown Tool ---

    @Test
    void shouldHandleUnknownToolCall() {
        ToolCall.FunctionCall fn = new ToolCall.FunctionCall("unknown_tool", "{}");
        ToolCall tc = new ToolCall("call_1", "function", fn);
        AssistantMessage toolMsg = AssistantMessage.ofToolCalls(List.of(tc));
        ChatResponse round1 = new ChatResponse("resp_1", "model",
                List.of(new ChatChoice(0, toolMsg, "tool_calls")), null);

        AssistantMessage finalMsg = AssistantMessage.ofText("Adjusted approach");
        ChatResponse round2 = new ChatResponse("resp_2", "model",
                List.of(new ChatChoice(0, finalMsg, "stop")), null);

        when(chatModel.chat(any())).thenReturn(round1, round2);
        // ToolRegistry returns unknown tool error
        when(toolRegistry.execute(any(), any()))
                .thenReturn(ToolResult.error("Unknown tool: unknown_tool"));

        AgentRequest request = buildRequest(8);
        task = buildTask(8);

        AgentResponse result = reActLoop.execute(request, toolContext, contextBuilder, conversation, task);

        assertThat(result.isSuccess()).isTrue(); // Agent continues
        ReActStep step = result.getSteps().get(0);
        assertThat(step.getObservation()).contains("Unknown tool");
    }

    // --- Max Turns ---

    @Test
    void shouldStopWhenMaxTurnsReached() {
        // LLM always returns tool calls
        ToolCall.FunctionCall fn = new ToolCall.FunctionCall("read_file", "{\"path\":\"pom.xml\"}");
        ToolCall tc = new ToolCall("call_1", "function", fn);
        AssistantMessage toolMsg = AssistantMessage.ofToolCalls(List.of(tc));
        ChatResponse response = new ChatResponse("resp_1", "model",
                List.of(new ChatChoice(0, toolMsg, "tool_calls")), null);

        when(chatModel.chat(any())).thenReturn(response);
        when(toolRegistry.execute(any(), any())).thenReturn(ToolResult.success("content"));

        AgentRequest request = buildRequest(3);
        task = buildTask(3);

        AgentResponse result = reActLoop.execute(request, toolContext, contextBuilder, conversation, task);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains("Reached maximum turns");
        assertThat(result.getError()).contains("3");
        assertThat(result.getTotalTurns()).isEqualTo(3);
        assertThat(task.getStatus()).isEqualTo(TaskStatus.HIT_MAX_TURNS);
    }

    // --- LLM API Failure ---

    @Test
    void shouldHandleLlmApiFailure() {
        when(chatModel.chat(any())).thenThrow(new ApiException(500, "Internal Server Error"));

        AgentRequest request = buildRequest(8);
        task = buildTask(8);

        AgentResponse result = reActLoop.execute(request, toolContext, contextBuilder, conversation, task);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains("LLM request failed");
        assertThat(task.getStatus()).isEqualTo(TaskStatus.FAILED);
    }

    // --- LLM Empty Response ---

    @Test
    void shouldHandleEmptyLlmResponse() {
        ChatResponse emptyResponse = new ChatResponse("resp_1", "model", List.of(), null);
        when(chatModel.chat(any())).thenReturn(emptyResponse);

        AgentRequest request = buildRequest(8);
        task = buildTask(8);

        AgentResponse result = reActLoop.execute(request, toolContext, contextBuilder, conversation, task);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains("empty response");
        assertThat(task.getStatus()).isEqualTo(TaskStatus.FAILED);
    }
}
