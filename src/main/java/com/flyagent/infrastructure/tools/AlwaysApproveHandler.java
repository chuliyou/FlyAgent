package com.flyagent.infrastructure.tools;

import com.flyagent.domain.tool.ApprovalHandler;
import com.flyagent.domain.tool.ApprovalRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 始终自动批准的审批处理器。
 * <p>
 * 用于测试环境或不需要人工审批的场景，自动通过所有审批请求。
 *
 * @author FlyAgent Team
 * @since 1.0
 */
public class AlwaysApproveHandler implements ApprovalHandler {
    private static final Logger log = LoggerFactory.getLogger(AlwaysApproveHandler.class);

    /**
     * 自动批准所有请求。
     *
     * @param request 审批请求
     * @return 始终返回 true
     */
    @Override
    public boolean approve(ApprovalRequest request) {
        log.info("Auto-approved: action={}, summary={}", request.getActionType(), request.getSummary());
        return true;
    }
}
