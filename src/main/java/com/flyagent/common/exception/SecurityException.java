package com.flyagent.common.exception;

/**
 * 安全异常。
 *
 * <p>当路径越权、命令包含危险模式等安全违规时抛出。</p>
 *
 * @author FlyAgent Team
 * @since 1.0
 */
public class SecurityException extends FlyAgentException {

    public SecurityException(String message) {
        super(message);
    }
}
