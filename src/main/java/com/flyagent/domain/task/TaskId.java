package com.flyagent.domain.task;

import java.util.Objects;
import java.util.UUID;

/**
 * 任务 ID 值对象。
 *
 * <p>由前缀 "task_" + UUID 前 8 位组成，全局唯一标识一次用户任务。</p>
 *
 * @author FlyAgent Team
 * @since 1.0
 */
public class TaskId {

    private final String value;

    private TaskId(String value) {
        this.value = value;
    }

    /**
     * 生成一个新的任务 ID。
     *
     * @return 新的 TaskId
     */
    public static TaskId generate() {
        return new TaskId("task_" + UUID.randomUUID().toString().substring(0, 8));
    }

    /**
     * 从已有值重建任务 ID。
     *
     * @param value ID 字符串值
     * @return TaskId
     * @throws IllegalArgumentException 当 value 为空时
     */
    public static TaskId of(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("TaskId must not be blank");
        }
        return new TaskId(value);
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TaskId)) {
            return false;
        }
        TaskId taskId = (TaskId) o;
        return value.equals(taskId.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
