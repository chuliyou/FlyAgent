package com.flyagent.interfaces.cli;

import com.flyagent.application.dto.AgentResponseDTO;
import com.flyagent.application.dto.SessionDTO;
import com.flyagent.application.service.AgentAppService;
import com.flyagent.application.service.SessionAppService;
import com.flyagent.common.exception.ApiException;
import com.flyagent.common.exception.ConfigException;
import com.flyagent.domain.agent.Conversation;
import com.flyagent.domain.session.AgentSession;
import com.flyagent.infrastructure.deepseek.DeepSeekConfig;

import java.util.Scanner;

/**
 * CLI 主控制器。
 *
 * <p>负责 CLI 交互主循环：读取输入 → 命令解析 → 分发处理 → 输出结果。</p>
 *
 * @author FlyAgent Team
 * @since 1.0
 */
public class AgentCli {

    private final AgentAppService agentService;
    private final SessionAppService sessionService;
    private final DeepSeekConfig config;
    private final String workspacePath;
    private final ConsolePresenter presenter;
    private boolean running;

    /**
     * 构造 CLI 控制器。
     *
     * @param agentService Agent 应用服务
     * @param sessionService 会话应用服务
     * @param config DeepSeek 配置
     * @param workspacePath 工作目录路径
     */
    public AgentCli(AgentAppService agentService, SessionAppService sessionService,
                    DeepSeekConfig config, String workspacePath) {
        this.agentService = agentService;
        this.sessionService = sessionService;
        this.config = config;
        this.workspacePath = workspacePath;
        this.presenter = new ConsolePresenter();
        this.running = true;
    }

    /**
     * 启动 CLI 交互循环。
     */
    public void start() {
        // 创建会话
        AgentSession session = sessionService.startSession(workspacePath);
        presenter.printWelcome(session, config);

        Scanner scanner = new Scanner(System.in);
        try {
            while (running) {
                presenter.printPrompt();
                if (!scanner.hasNextLine()) {
                    break;
                }
                String input = scanner.nextLine();

                ParsedCommand command = CommandParser.parse(input);

                if (command.isEmpty()) {
                    continue;
                }

                switch (command.getType()) {
                    case BUILTIN:
                        handleBuiltin(command.getBuiltinCommand(), scanner);
                        break;
                    case USER_INPUT:
                        handleUserInput(command.getUserInput());
                        break;
                    default:
                        break;
                }
            }
        } finally {
            scanner.close();
        }

        sessionService.closeSession();
    }

    private void handleBuiltin(BuiltinCommand cmd, Scanner scanner) {
        switch (cmd) {
            case HELP:
                presenter.printHelp();
                break;
            case CLEAR:
                handleClear(scanner);
                break;
            case COMPACT:
                handleCompact(scanner);
                break;
            case EXIT:
                presenter.printGoodbye();
                running = false;
                break;
            case PWD:
                AgentSession session = sessionService.getCurrentSession();
                if (session != null) {
                    presenter.printPwd(session.getWorkspace().getDisplayPath());
                }
                break;
            case SESSION:
                AgentSession sess = sessionService.getCurrentSession();
                if (sess != null) {
                    SessionDTO dto = new SessionDTO(
                            sess.getId().getValue(),
                            sess.getWorkspace().getDisplayPath(),
                            sess.getStatus().name(),
                            sess.getCreatedAt().toString()
                    );
                    presenter.printSessionInfo(dto);
                }
                break;
            default:
                break;
        }
    }

    /**
     * 处理 /clear 命令：确认后清空对话历史。
     */
    private void handleClear(Scanner scanner) {
        if (!confirmAction("clear all conversation history", scanner)) {
            presenter.printCancelled();
            return;
        }
        sessionService.clearConversation();
        presenter.printClear();
    }

    /**
     * 处理 /compact 命令：确认后压缩对话历史为摘要。
     */
    private void handleCompact(Scanner scanner) {
        if (!confirmAction("compact conversation history", scanner)) {
            presenter.printCancelled();
            return;
        }

        Conversation conv = sessionService.getCurrentSession() != null
                ? sessionService.getCurrentSession().getConversation() : null;
        if (conv == null || conv.getMessages().isEmpty()) {
            presenter.printCompactEmpty();
            return;
        }

        presenter.printCompactStart();
        try {
            String result = sessionService.compactConversation(workspacePath);
            presenter.printCompactDone(result);
        } catch (Exception e) {
            presenter.printApiError("Compact failed: " + e.getMessage());
        }
    }

    /**
     * 用户确认提示：输出确认信息，等待 y/n 输入。
     *
     * @param action  描述待确认操作的文本
     * @param scanner 共享的输入扫描器
     * @return true 表示用户确认
     */
    private boolean confirmAction(String action, Scanner scanner) {
        presenter.printConfirm(action);
        try {
            if (scanner.hasNextLine()) {
                String response = scanner.nextLine().trim().toLowerCase();
                return "y".equals(response) || "yes".equals(response);
            }
        } catch (Exception ignored) {
            // If we can't read input, deny the action
        }
        return false;
    }

    private void handleUserInput(String input) {
        try {
            AgentResponseDTO response = agentService.handleUserInput(input);
            presenter.printResponse(response);
        } catch (ConfigException e) {
            presenter.printApiError(e.getMessage());
        } catch (ApiException e) {
            presenter.printApiError("API error [" + e.getStatusCode() + "]: "
                    + e.getMessage());
        } catch (Exception e) {
            presenter.printApiError("Unexpected error: " + e.getMessage());
        }
    }
}
