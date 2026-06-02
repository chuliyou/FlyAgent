package com.flyagent.domain.chat;

import com.flyagent.common.exception.ApiException;

/**
 * 聊天模型领域端口。
 *
 * <p>定义模型调用抽象，基础设施层负责提供具体实现（如 DeepSeek）。
 * 后续所有上层模块只依赖此端口，不直接感知具体模型供应商。</p>
 *
 * @author FlyAgent Team
 * @since 1.0
 */
public interface ChatModelPort {

    /**
     * 发送聊天请求并获取响应。
     *
     * @param request 聊天请求（包含消息列表和可选工具定义）
     * @return 聊天响应（包含助手消息和 token 用量）
     * @throws ApiException 当 API 调用失败时
     */
    ChatResponse chat(ChatRequest request);
}
