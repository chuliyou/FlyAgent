package com.flyagent.domain.tool;

import java.util.List;
import java.util.Optional;

/**
 * 工具注册表端口接口。
 *
 * <p>管理工具注册、查找和执行的统一入口。
 * 领域层定义契约，基础设施层提供线程安全实现。</p>
 *
 * @author FlyAgent Team
 * @since 1.0
 */
public interface ToolRegistry {

    /**
     * 注册工具，重复名称抛出 IllegalArgumentException。
     */
    void register(Tool tool);

    /**
     * 按名称查找工具。
     */
    Optional<Tool> find(String name);

    /**
     * 获取所有已注册工具的定义列表（可直接传入 ChatRequest.tools）。
     */
    List<ToolDefinition> definitions();

    /**
     * 执行工具调用。
     */
    ToolResult execute(ToolCall call, ToolExecutionContext context);

    /**
     * 批量注册。
     */
    default void registerAll(Tool... tools) {
        for (Tool t : tools) {
            register(t);
        }
    }
}
