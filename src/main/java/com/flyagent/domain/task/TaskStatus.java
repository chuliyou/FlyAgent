package com.flyagent.domain.task;

/**
 * 任务状态枚举。
 *
 * <p>定义一次用户任务从创建到结束的完整生命周期状态。</p>
 *
 * @author FlyAgent Team
 * @since 1.0
 */
public enum TaskStatus {

    /** 已创建，等待开始 */
    CREATED,

    /** 执行中 */
    RUNNING,

    /** 等待用户审批 */
    WAITING_APPROVAL,

    /** 已完成 */
    COMPLETED,

    /** 执行失败 */
    FAILED,

    /** 达到最大轮次上限 */
    HIT_MAX_TURNS,

    /** 用户拒绝审批 */
    REJECTED
}
