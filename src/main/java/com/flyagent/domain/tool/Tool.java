package com.flyagent.domain.tool;

/**
 * 工具统一端口接口。
 *
 * <p>所有本地工具必须实现此接口。遵循端口/适配器模式：
 * 领域层定义契约，基础设施层提供具体实现。</p>
 *
 * <p>实现要求：execute() 方法必须捕获所有异常，
 * 转换为 ToolResult.error() 返回，不得抛出到调用方。</p>
 *
 * @author FlyAgent Team
 * @since 1.0
 */
public interface Tool {

    /**
     * 工具名称，全局唯一，对应 DeepSeek function.name。
     */
    String name();

    /**
     * 工具用途描述，用于模型理解何时调用该工具。
     */
    String description();

    /**
     * 生成工具定义（function calling 协议格式），可直接放入 ChatRequest.tools。
     */
    ToolDefinition definition();

    /**
     * 执行工具。
     *
     * @param call    工具调用请求（来自 DeepSeek 响应的 tool_calls）
     * @param context 执行上下文（workspace、超时、审批处理器等）
     * @return 执行结果，实现必须内部捕获异常，不得抛出
     */
    ToolResult execute(ToolCall call, ToolExecutionContext context);
}
