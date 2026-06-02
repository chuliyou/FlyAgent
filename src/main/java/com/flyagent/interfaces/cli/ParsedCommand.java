package com.flyagent.interfaces.cli;

/**
 * 解析后的命令。
 *
 * <p>对用户输入进行分类：空输入、内置命令或自然语言任务。</p>
 *
 * @author FlyAgent Team
 * @since 1.0
 */
public class ParsedCommand {

    /** 命令类型 */
    private final CommandType type;

    /** 内置命令（仅当 type=BUILTIN 时有值） */
    private final BuiltinCommand builtinCommand;

    /** 用户自然语言输入（仅当 type=USER_INPUT 时有值） */
    private final String userInput;

    private ParsedCommand(CommandType type, BuiltinCommand builtinCommand, String userInput) {
        this.type = type;
        this.builtinCommand = builtinCommand;
        this.userInput = userInput;
    }

    public static ParsedCommand builtin(BuiltinCommand cmd) {
        return new ParsedCommand(CommandType.BUILTIN, cmd, null);
    }

    public static ParsedCommand userInput(String input) {
        return new ParsedCommand(CommandType.USER_INPUT, null, input);
    }

    public static ParsedCommand empty() {
        return new ParsedCommand(CommandType.EMPTY, null, null);
    }

    public CommandType getType() {
        return type;
    }

    public BuiltinCommand getBuiltinCommand() {
        return builtinCommand;
    }

    public String getUserInput() {
        return userInput;
    }

    public boolean isEmpty() {
        return type == CommandType.EMPTY;
    }

    /**
     * 命令类型枚举。
     */
    public enum CommandType {
        EMPTY,
        BUILTIN,
        USER_INPUT
    }
}
