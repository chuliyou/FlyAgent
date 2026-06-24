package com.flyagent.domain.agent;

import com.flyagent.common.exception.ApiException;
import com.flyagent.domain.chat.ChatChoice;
import com.flyagent.domain.chat.ChatModelPort;
import com.flyagent.domain.chat.ChatRequest;
import com.flyagent.domain.chat.ChatResponse;
import com.flyagent.domain.message.AssistantMessage;
import com.flyagent.domain.task.AgentTask;
import com.flyagent.domain.tool.ToolCall;
import com.flyagent.domain.tool.ToolDefinition;
import com.flyagent.domain.tool.ToolExecutionContext;
import com.flyagent.domain.tool.ToolRegistry;
import com.flyagent.domain.tool.ToolResult;
import com.flyagent.infrastructure.tools.ToolArgumentParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ReAct 循环领域服务。
 *
 * <p>实现标准的 ReAct (Reasoning + Acting) 循环：
 * Thought → Action → Observation 的迭代执行，
 * 直到获得 Final Answer 或触发终止条件。</p>
 *
 * <p>不直接依赖任何基础设施实现，所有外部能力通过端口注入。</p>
 *
 * @author FlyAgent Team
 * @since 1.0
 */
public class ReActLoop {

    private static final int THOUGHT_MAX_CHARS = 200;

    private final ChatModelPort chatModel;
    private final ToolRegistry toolRegistry;

    /**
     * 构造 ReActLoop。
     *
     * @param chatModel    LLM 调用端口
     * @param toolRegistry 工具注册表端口
     */
    public ReActLoop(ChatModelPort chatModel, ToolRegistry toolRegistry) {
        this.chatModel = chatModel;
        this.toolRegistry = toolRegistry;
    }

    /**
     * 执行 ReAct 循环。
     *
     * @param agentRequest   用户请求
     * @param toolContext    工具执行上下文
     * @param contextBuilder 上下文构建服务
     * @param conversation   会话上下文
     * @param task           任务聚合根（用于状态机管理）
     * @return 完整 AgentResponse
     */
    public AgentResponse execute(
            AgentRequest agentRequest,
            ToolExecutionContext toolContext,
            ContextBuildService contextBuilder,
            Conversation conversation,
            AgentTask task) {

        task.start();
        List<ReActStep> steps = new ArrayList<>();
        List<ToolDefinition> toolDefs = toolRegistry.definitions();

        while (task.canContinue()) {
            // 1. 构建请求
            ChatRequest chatRequest = contextBuilder.build(
                    conversation, toolDefs, agentRequest.getWorkspace());

            // 2. 调用 LLM
            ChatResponse response;
            try {
                response = chatModel.chat(chatRequest);
            } catch (ApiException e) {
                task.fail("LLM request failed: " + e.getMessage());
                return AgentResponse.error(
                        "LLM request failed: " + e.getMessage(),
                        steps, task.getCurrentTurn());
            }

            // 3. 解析响应
            ChatChoice choice = response.firstChoice();
            if (choice == null) {
                task.fail("Model returned empty response");
                return AgentResponse.error(
                        "Model returned empty response",
                        steps, task.getCurrentTurn());
            }

            AssistantMessage msg = choice.getMessage();
            String finishReason = choice.getFinishReason();

            // 4. 情况 A：Final Answer（content 非空 + 无 tool_calls）
            if (!msg.hasToolCalls() && msg.hasContent()
                    && !"tool_calls".equals(finishReason)) {
                String thought = extractThought(msg);
                ReActStep step = ReActStep.builder()
                        .turn(task.getCurrentTurn() + 1)
                        .thought(thought)
                        .finalAnswer(msg.getContent())
                        .success(true)
                        .build();
                steps.add(step);
                conversation.addAssistantMessage(msg.getContent());
                task.complete(msg.getContent());
                return AgentResponse.success(
                        msg.getContent(), steps, task.getCurrentTurn() + 1);
            }

            // 5. 情况 B：Tool Calls
            if (msg.hasToolCalls()) {
                String thought = extractThought(msg);
                conversation.addAssistantToolCalls(msg.getToolCalls());

                for (ToolCall tc : msg.getToolCalls()) {
                    ToolResult result = toolRegistry.execute(tc, toolContext);

                    Map<String, Object> actionArgs = null;
                    try {
                        actionArgs = ToolArgumentParser.parseArguments(tc);
                    } catch (Exception ignored) {
                        // keep null for failed parse
                    }

                    String observation = buildObservation(result);
                    ReActStep step = ReActStep.builder()
                            .turn(task.getCurrentTurn() + 1)
                            .thought(thought)
                            .actionName(tc.getFunction().getName())
                            .actionArguments(actionArgs)
                            .observation(observation)
                            .success(result.isSuccess())
                            .error(result.isSuccess() ? null : result.getError())
                            .build();
                    steps.add(step);

                    // 写入 Observation 到 Conversation
                    String toolContent = result.isSuccess()
                            ? result.getContent()
                            : "Error: " + result.getError();
                    conversation.addToolMessage(tc.getId(), toolContent);
                }

                task.increaseTurn();
                continue;
            }

            // 6. 情况 C：既无 content 也无 tool_calls
            task.fail("Model returned neither content nor tool calls");
            return AgentResponse.error(
                    "Model returned empty response", steps, task.getCurrentTurn());
        }

        // 循环结束：达到最大轮次
        task.hitMaxTurns();
        return AgentResponse.error(
                "Reached maximum turns (" + agentRequest.getMaxTurns()
                        + ") without completing the task.",
                steps, task.getCurrentTurn());
    }

    /**
     * 从 AssistantMessage 提取思考摘要（取 content 前 200 字符）。
     */
    private String extractThought(AssistantMessage msg) {
        if (msg.hasContent()) {
            String content = msg.getContent();
            if (content.length() <= THOUGHT_MAX_CHARS) {
                return content;
            }
            return content.substring(0, THOUGHT_MAX_CHARS) + "...";
        }
        return "Calling tools...";
    }

    /**
     * 构建 Observation 文本。
     */
    private String buildObservation(ToolResult result) {
        if (result.isSuccess()) {
            String content = result.getContent();
            return content != null ? content : "";
        }
        return "Error: " + result.getError();
    }
}
