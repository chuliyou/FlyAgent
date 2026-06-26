package com.flyagent.interfaces.cli;

import com.flyagent.interfaces.cli.ParsedCommand.CommandType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CommandParser 单元测试。
 *
 * @author FlyAgent Team
 * @since 1.0
 */
class CommandParserTest {

    @Test
    @DisplayName("/compact 应解析为 COMPACT 内置命令")
    void shouldParseCompactSlash() {
        ParsedCommand cmd = CommandParser.parse("/compact");
        assertThat(cmd.getType()).isEqualTo(CommandType.BUILTIN);
        assertThat(cmd.getBuiltinCommand()).isEqualTo(BuiltinCommand.COMPACT);
    }

    @Test
    @DisplayName("compact（无斜线）应解析为 COMPACT 内置命令")
    void shouldParseCompactPlain() {
        ParsedCommand cmd = CommandParser.parse("compact");
        assertThat(cmd.getType()).isEqualTo(CommandType.BUILTIN);
        assertThat(cmd.getBuiltinCommand()).isEqualTo(BuiltinCommand.COMPACT);
    }

    @Test
    @DisplayName("COMPACT（大写）应解析为 COMPACT 内置命令")
    void shouldParseCompactUppercase() {
        ParsedCommand cmd = CommandParser.parse("COMPACT");
        assertThat(cmd.getType()).isEqualTo(CommandType.BUILTIN);
        assertThat(cmd.getBuiltinCommand()).isEqualTo(BuiltinCommand.COMPACT);
    }

    @Test
    @DisplayName("非命令自然语言输入应解析为 USER_INPUT")
    void shouldParseNaturalLanguageAsUserInput() {
        ParsedCommand cmd = CommandParser.parse("How do I compact my code?");
        assertThat(cmd.getType()).isEqualTo(CommandType.USER_INPUT);
        assertThat(cmd.getUserInput()).isEqualTo("How do I compact my code?");
    }

    @Test
    @DisplayName("/clear 应解析为 CLEAR 内置命令")
    void shouldParseClear() {
        ParsedCommand cmd = CommandParser.parse("/clear");
        assertThat(cmd.getType()).isEqualTo(CommandType.BUILTIN);
        assertThat(cmd.getBuiltinCommand()).isEqualTo(BuiltinCommand.CLEAR);
    }

    @Test
    @DisplayName("空输入应解析为 EMPTY")
    void shouldParseEmptyAsEmpty() {
        ParsedCommand cmd = CommandParser.parse("");
        assertThat(cmd.getType()).isEqualTo(CommandType.EMPTY);
    }

    @Test
    @DisplayName("null 输入应解析为 EMPTY")
    void shouldParseNullAsEmpty() {
        ParsedCommand cmd = CommandParser.parse(null);
        assertThat(cmd.getType()).isEqualTo(CommandType.EMPTY);
    }
}
