package com.flyagent.common.exception;

/**
 * 配置异常。
 *
 * <p>当配置文件缺失、配置项无效或必要环境变量未设置时抛出。</p>
 *
 * @author FlyAgent Team
 * @since 1.0
 */
public class ConfigException extends FlyAgentException {

    public ConfigException(String message) {
        super(message);
    }
}
