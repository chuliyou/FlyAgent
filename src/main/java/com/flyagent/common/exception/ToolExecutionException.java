package com.flyagent.common.exception;

/**
 * 工具执行异常。
 *
 * <p>当工具执行过程中发生预期内的失败（文件不存在、超时等）时抛出。
 * 工具实现内部应捕获此异常并转换为 ToolResult.error()。</p>
 *
 * @author FlyAgent Team
 * @since 1.0
 */
public class ToolExecutionException extends FlyAgentException {

    public ToolExecutionException(String message) {
        super(message);
    }

    public ToolExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
