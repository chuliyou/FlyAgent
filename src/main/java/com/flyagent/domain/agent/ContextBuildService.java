package com.flyagent.domain.agent;

import com.flyagent.domain.chat.ChatRequest;
import com.flyagent.domain.message.Message;
import com.flyagent.domain.message.SystemMessage;
import com.flyagent.domain.tool.ToolDefinition;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 上下文构建领域服务。
 *
 * <p>负责组装发送给 LLM 的完整请求上下文，包括：
 * System Prompt、工具定义列表、历史对话消息。</p>
 *
 * <p>不依赖任何基础设施实现，所有输入通过参数注入。</p>
 *
 * @author FlyAgent Team
 * @since 1.0
 */
public class ContextBuildService {

    /**
     * 构建 ChatRequest。
     *
     * @param conversation   当前会话上下文
     * @param toolDefinitions 可用工具定义列表
     * @param workspacePath  当前工作目录路径
     * @return 完整 ChatRequest（含 System Prompt、历史消息、工具定义）
     */
    public ChatRequest build(
            Conversation conversation,
            List<ToolDefinition> toolDefinitions,
            Path workspacePath) {

        // 1. 构建 System Prompt
        String systemPrompt = buildSystemPrompt(workspacePath);

        // 2. 组装消息列表：System Message 始终在首位
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt));
        messages.addAll(conversation.getHistoryMessages());

        // 3. 构建 ChatRequest
        ChatRequest.Builder builder = ChatRequest.builder()
                .messages(messages)
                .thinking(true)
                .stream(false);

        if (toolDefinitions != null && !toolDefinitions.isEmpty()) {
            builder.tools(toolDefinitions);
        }

        return builder.build();
    }

    /**
     * 生成 System Prompt。
     */
    private String buildSystemPrompt(Path workspacePath) {
        return String.format("""
                You are FlyAgent, a CLI coding agent running on the user's local machine.

                You operate in a ReAct (Reasoning + Acting) loop:
                - **Thought**: Briefly explain your next step.
                - **Action**: Call a tool when you need to read files, list directories,
                  run commands, or modify the project.
                - **Observation**: The tool result will be provided. Never fabricate
                  observations — only use actual tool outputs.
                - **Final Answer**: Provide your final response to the user when the task
                  is complete. Do NOT ask the user for permission or confirmation.

                Available tools are listed in the function definitions. Use them to:
                - Read and analyze files
                - List directory structures
                - Run shell commands (with approval)
                - Write or modify files (with approval)
                - Create project structures

                Current workspace: %s
                Current date: %s

                Important rules:
                1. Always use tools to gather information — do not guess.
                2. If a tool fails, report the error and try an alternative approach.
                3. Do not call tools that are not in the available tools list.
                4. Stop when the user's task is complete and provide a concise Final Answer.
                """,
                workspacePath.toAbsolutePath(),
                LocalDate.now().toString());
    }
}
