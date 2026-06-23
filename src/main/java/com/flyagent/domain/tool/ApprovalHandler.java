package com.flyagent.domain.tool;

/**
 * 审批处理器端口接口。
 *
 * <p>工具层通过此端口请求用户审批，不同场景切换不同实现。
 * 策略模式：AlwaysApproveHandler（测试）、ConsoleApprovalHandler（CLI 交互）等。</p>
 *
 * @author FlyAgent Team
 * @since 1.0
 */
@FunctionalInterface
public interface ApprovalHandler {

    /**
     * 请求审批。
     *
     * @param request 审批请求（包含操作类型、摘要和详细信息）
     * @return true 表示审批通过，false 表示拒绝
     */
    boolean approve(ApprovalRequest request);
}
