package com.flyagent.infrastructure.tools;

import com.flyagent.common.exception.SecurityException;
import com.flyagent.domain.tool.Tool;
import com.flyagent.domain.tool.ToolCall;
import com.flyagent.domain.tool.ToolDefinition;
import com.flyagent.domain.tool.ToolExecutionContext;
import com.flyagent.domain.tool.ToolRegistry;
import com.flyagent.domain.tool.ToolResult;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ToolRegistry 的默认实现。
 *
 * <p>线程安全的工具注册表，使用 ConcurrentHashMap 存储。
 * 提供双层安全网：工具内部异常捕获 + 注册表层兜底捕获。</p>
 *
 * @author FlyAgent Team
 * @since 1.0
 */
public class ToolRegistryImpl implements ToolRegistry {

    private final Map<String, Tool> tools = new ConcurrentHashMap<>();

    @Override
    public void register(Tool tool) {
        if (tools.containsKey(tool.name())) {
            throw new IllegalArgumentException(
                    "Tool already registered: " + tool.name());
        }
        tools.put(tool.name(), tool);
    }

    @Override
    public Optional<Tool> find(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    @Override
    public List<ToolDefinition> definitions() {
        return tools.values().stream()
                .map(Tool::definition)
                .toList();
    }

    @Override
    public ToolResult execute(ToolCall call, ToolExecutionContext context) {
        String toolName = call.getFunction().getName();
        Optional<Tool> tool = find(toolName);
        if (tool.isEmpty()) {
            return ToolResult.error("Unknown tool: " + toolName);
        }
        try {
            return tool.get().execute(call, context);
        } catch (SecurityException e) {
            return ToolResult.error(e.getMessage(),
                    Map.of("security_violation", true));
        } catch (Exception e) {
            return ToolResult.error(
                    "Tool execution failed: " + e.getMessage(),
                    Map.of("exception_type", e.getClass().getSimpleName()));
        }
    }
}
