package com.flyagent.common.exception;

/**
 * API 调用异常。
 *
 * <p>当 DeepSeek API 请求失败、响应解析错误时抛出。携带 HTTP 状态码供上层分流处理。</p>
 *
 * @author FlyAgent Team
 * @since 1.0
 */
public class ApiException extends FlyAgentException {

    /** HTTP 状态码，0 表示非 HTTP 错误（如网络异常、解析失败） */
    private final int statusCode;

    public ApiException(String message) {
        super(message);
        this.statusCode = 0;
    }

    public ApiException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 0;
    }

    public ApiException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
