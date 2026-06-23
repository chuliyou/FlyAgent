package com.flyagent.domain.tool;

import com.flyagent.common.exception.SecurityException;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 命令安全检查器。
 * <p>
 * 通过正则表达式匹配检测危险命令，防止执行可能造成系统破坏的操作。
 *
 * @author FlyAgent Team
 * @since 1.0
 */
public class CommandSafetyChecker {

    private static final List<DangerPattern> DANGER_PATTERNS = List.of(
        DangerPattern.of("rm -rf /", "(?i)\\brm\\s+-rf\\s+/\\b"),
        DangerPattern.of("rm -rf /*", "(?i)\\brm\\s+-rf\\s+/\\*\\b"),
        DangerPattern.of("rm -rf ~", "(?i)\\brm\\s+-rf\\s+~\\b"),
        DangerPattern.of("del /s /q C:\\", "(?i)\\bdel\\s+/[sS]\\s+/[qQ]\\s+[A-Z]:\\\\"),
        DangerPattern.of("git reset --hard", "(?i)\\bgit\\s+reset\\s+--hard\\b"),
        DangerPattern.of("git push --force origin main", "(?i)\\bgit\\s+push\\s+.*--force\\s+origin\\s+(main|master)\\b"),
        DangerPattern.of("format disk", "(?i)\\bformat\\s+[A-Z]:\\b"),
        DangerPattern.of("shutdown", "(?i)\\bshutdown\\b"),
        DangerPattern.of("reboot", "(?i)\\breboot\\b"),
        DangerPattern.of("dd if=", "(?i)\\bdd\\s+if="),
        DangerPattern.of("mkfs", "(?i)\\bmkfs\\b"),
        DangerPattern.of("chmod 777 /", "(?i)\\bchmod\\s+777\\s+/"),
        DangerPattern.of("fork bomb", "(?i):\\(\\)\\s*\\{\\s*:\\|:&\\s*\\};:")
    );

    /**
     * 检测命令中的危险模式。
     *
     * @param command 待检测的命令字符串
     * @return 匹配到的危险模式描述，未匹配则返回 null
     */
    public String detectDanger(String command) {
        if (command == null || command.isBlank()) return null;
        for (DangerPattern dp : DANGER_PATTERNS) {
            if (dp.pattern.matcher(command).find()) return dp.description;
        }
        return null;
    }

    /**
     * 判断命令是否包含危险模式。
     *
     * @param command 待判断的命令字符串
     * @return true 如果检测到危险模式
     */
    public boolean isDangerous(String command) {
        return detectDanger(command) != null;
    }

    /**
     * 检查命令是否危险，若危险则抛出异常。
     *
     * @param command 待检查的命令字符串
     * @throws SecurityException 如果检测到危险模式
     */
    public void check(String command) {
        String detected = detectDanger(command);
        if (detected != null) {
            throw new SecurityException(
                "Dangerous command detected: " + detected + ". Command refused for safety.");
        }
    }

    /**
     * 获取所有已注册的危险模式描述列表。
     *
     * @return 危险模式描述列表
     */
    public List<String> getDangerPatterns() {
        return DANGER_PATTERNS.stream().map(dp -> dp.description).toList();
    }

    /**
     * 危险模式内部数据类。
     */
    private static class DangerPattern {
        final String description;
        final Pattern pattern;

        DangerPattern(String description, Pattern pattern) {
            this.description = description;
            this.pattern = pattern;
        }

        static DangerPattern of(String description, String regex) {
            return new DangerPattern(description, Pattern.compile(regex));
        }
    }
}
