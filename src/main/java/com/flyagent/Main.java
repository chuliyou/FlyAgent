package com.flyagent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flyagent.application.service.AgentAppService;
import com.flyagent.application.service.SessionAppService;
import com.flyagent.domain.chat.ChatModelPort;
import com.flyagent.infrastructure.config.AppConfig;
import com.flyagent.infrastructure.deepseek.DeepSeekChatModel;
import com.flyagent.infrastructure.deepseek.DeepSeekConfig;
import com.flyagent.interfaces.cli.AgentCli;

/**
 * FlyAgent 应用入口。
 *
 * <p>负责组装依赖并启动 CLI 交互式会话。依赖组装顺序：</p>
 * <ol>
 *   <li>加载配置（环境变量）</li>
 *   <li>初始化 DeepSeek 客户端（infrastructure）</li>
 *   <li>初始化应用服务（application）</li>
 *   <li>启动 CLI 交互循环（interfaces）</li>
 * </ol>
 *
 * @author FlyAgent Team
 * @since 1.0
 */
public class Main {

    /**
     * 应用程序主入口。
     *
     * @param args 命令行参数，args[0] 可选指定 workspace 路径
     */
    public static void main(String[] args) {
        // 1. 加载配置
        AppConfig appConfig = new AppConfig();
        DeepSeekConfig dsConfig = appConfig.getDeepSeekConfig();

        // 2. 初始化基础设施
        ObjectMapper objectMapper = new ObjectMapper();
        ChatModelPort chatModel = new DeepSeekChatModel(dsConfig, objectMapper);

        // 3. 初始化应用服务
        SessionAppService sessionService = new SessionAppService();
        AgentAppService agentService = new AgentAppService(chatModel, sessionService);

        // 4. 解析 workspace（命令行参数优先，默认当前目录）
        String workspace = args.length > 0 ? args[0] : System.getProperty("user.dir");

        // 5. 启动 CLI
        AgentCli cli = new AgentCli(agentService, sessionService, dsConfig, workspace);
        cli.start();
    }
}
