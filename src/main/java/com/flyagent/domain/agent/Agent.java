package com.flyagent.domain.agent;

/**
 * Agent 领域端口。
 *
 * <p>定义 Agent 执行的统一入口。领域层定义契约，
 * 不同 Agent 策略（ReAct、Plan-and-Execute）通过不同实现提供。</p>
 *
 * <p>Agent 层只依赖端口接口（{@link com.flyagent.domain.chat.ChatModelPort}、
 * {@link com.flyagent.domain.tool.ToolRegistry}），不直接感知具体实现。</p>
 *
 * @author FlyAgent Team
 * @since 1.0
 */
public interface Agent {

    /**
     * 执行 Agent 任务。
     *
     * @param request Agent 请求（包含用户输入、workspace、maxTurns 等）
     * @return Agent 响应（包含最终回答和执行轨迹）
     */
    AgentResponse run(AgentRequest request);
}
