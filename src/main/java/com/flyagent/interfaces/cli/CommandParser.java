package com.flyagent.interfaces.cli;

/**
 * 命令解析器。
 *
 * <p>对用户输入进行分类，区分内置命令与自然语言任务。</p>
 *
 * @author FlyAgent Team
 * @since 1.0
 */
public final class CommandParser {

    private CommandParser() {
    }

    /**
     * 解析用户输入。
     *
     * @param input 原始输入
     * @return 解析后的命令
     */
    public static ParsedCommand parse(String input) {
        if (input == null || input.isBlank()) {
            return ParsedCommand.empty();
        }

        String trimmed = input.trim();

        // 内置命令匹配
        if (isHelp(trimmed)) {
            return ParsedCommand.builtin(BuiltinCommand.HELP);
        }
        if (isClear(trimmed)) {
            return ParsedCommand.builtin(BuiltinCommand.CLEAR);
        }
        if (isExit(trimmed)) {
            return ParsedCommand.builtin(BuiltinCommand.EXIT);
        }
        if (isPwd(trimmed)) {
            return ParsedCommand.builtin(BuiltinCommand.PWD);
        }
        if (isSession(trimmed)) {
            return ParsedCommand.builtin(BuiltinCommand.SESSION);
        }
        if (isCompact(trimmed)) {
            return ParsedCommand.builtin(BuiltinCommand.COMPACT);
        }

        // 默认：自然语言用户输入
        return ParsedCommand.userInput(trimmed);
    }

    private static boolean isHelp(String input) {
        return "/help".equals(input) || "help".equalsIgnoreCase(input);
    }

    private static boolean isClear(String input) {
        return "/clear".equals(input) || "clear".equalsIgnoreCase(input);
    }

    private static boolean isExit(String input) {
        return "/exit".equals(input) || "exit".equalsIgnoreCase(input)
                || "quit".equalsIgnoreCase(input);
    }

    private static boolean isPwd(String input) {
        return "/pwd".equals(input) || "pwd".equalsIgnoreCase(input);
    }

    private static boolean isSession(String input) {
        return "/session".equals(input);
    }

    private static boolean isCompact(String input) {
        return "/compact".equals(input) || "compact".equalsIgnoreCase(input);
    }
}
