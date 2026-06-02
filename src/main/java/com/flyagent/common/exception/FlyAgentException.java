package com.flyagent.common.exception;

/**
 * FlyAgent 基础异常。
 *
 * <p>所有 FlyAgent 自定义异常的父类，用于统一异常体系。</p>
 *
 * @author FlyAgent Team
 * @since 1.0
 */
public class FlyAgentException extends RuntimeException {

    public FlyAgentException(String message) {
        super(message);
    }

    public FlyAgentException(String message, Throwable cause) {
        super(message, cause);
    }
}
