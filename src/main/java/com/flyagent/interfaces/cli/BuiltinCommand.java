package com.flyagent.interfaces.cli;

/**
 * CLI 内置命令枚举。
 *
 * @author FlyAgent Team
 * @since 1.0
 */
public enum BuiltinCommand {

    /** 显示帮助信息 */
    HELP,

    /** 清空会话上下文 */
    CLEAR,

    /** 退出 CLI */
    EXIT,

    /** 显示当前工作目录 */
    PWD,

    /** 显示当前会话信息 */
    SESSION
}
