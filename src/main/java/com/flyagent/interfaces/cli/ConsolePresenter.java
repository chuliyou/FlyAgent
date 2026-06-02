package com.flyagent.interfaces.cli;

import com.flyagent.application.dto.AgentResponseDTO;
import com.flyagent.application.dto.SessionDTO;
import com.flyagent.domain.session.AgentSession;
import com.flyagent.infrastructure.deepseek.DeepSeekConfig;

/**
 * 控制台展示器。
 *
 * <p>负责格式化 CLI 输出：欢迎信息、帮助、结果、错误等。</p>
 *
 * @author FlyAgent Team
 * @since 1.0
 */
public class ConsolePresenter {

    /**
     * 打印欢迎信息。
     *
     * @param session 当前会话
     * @param config DeepSeek 配置
     */
    public void printWelcome(AgentSession session, DeepSeekConfig config) {
        System.out.println("FlyAgent started.");
        System.out.println("Provider: DeepSeek");
        System.out.println("Model: " + config.getDefaultModel());
        System.out.println("Workspace: " + session.getWorkspace().getDisplayPath());
        System.out.println("Session: " + session.getId().getValue());
        System.out.println("Type /help for commands.");
        System.out.println();
    }

    /**
     * 打印输入提示符。
     */
    public void printPrompt() {
        System.out.print("> ");
    }

    /**
     * 打印帮助信息。
     */
    public void printHelp() {
        System.out.println();
        System.out.println("Available commands:");
        System.out.println("  /help      Show this help");
        System.out.println("  /clear     Clear current session context");
        System.out.println("  /exit      Exit FlyAgent");
        System.out.println("  /pwd       Show current workspace");
        System.out.println("  /session   Show current session info");
        System.out.println();
        System.out.println("Or just type your coding task in natural language.");
        System.out.println();
    }

    /**
     * 打印会话信息。
     *
     * @param dto 会话 DTO
     */
    public void printSessionInfo(SessionDTO dto) {
        System.out.println();
        System.out.println("Session ID: " + dto.getSessionId());
        System.out.println("Workspace:  " + dto.getWorkspace());
        System.out.println("Status:     " + dto.getStatus());
        System.out.println("Created:    " + dto.getCreatedAt());
        System.out.println();
    }

    /**
     * 打印当前工作目录。
     *
     * @param workspace 工作目录路径
     */
    public void printPwd(String workspace) {
        System.out.println(workspace);
    }

    /**
     * 打印清空上下文提示。
     */
    public void printClear() {
        System.out.println("Context cleared.");
    }

    /**
     * 打印退出消息。
     */
    public void printGoodbye() {
        System.out.println("Goodbye!");
    }

    /**
     * 打印 Agent 响应。
     *
     * @param response 响应 DTO
     */
    public void printResponse(AgentResponseDTO response) {
        if (response.isSuccess()) {
            System.out.println();
            System.out.println(response.getContent());
            if (response.getTokenUsage() != null) {
                System.out.println();
                System.out.println("[Tokens: "
                        + response.getTokenUsage().getPromptTokens() + " in / "
                        + response.getTokenUsage().getCompletionTokens() + " out]");
            }
        } else {
            System.out.println();
            System.out.println("Error: " + response.getErrorMessage());
        }
        System.out.println();
    }

    /**
     * 打印 API 错误。
     *
     * @param message 错误消息
     */
    public void printApiError(String message) {
        System.out.println();
        System.out.println("Error: " + message);
        System.out.println();
    }
}
