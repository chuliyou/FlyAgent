package com.flyagent.domain.tool;

import java.util.Map;

/**
 * 审批请求值对象。
 *
 * <p>封装工具执行前的审批信息，包含操作类型、摘要和详细信息。</p>
 *
 * @author FlyAgent Team
 * @since 1.0
 */
public class ApprovalRequest {

    private final String actionType;
    private final String summary;
    private final Map<String, Object> details;

    public ApprovalRequest(String actionType, String summary, Map<String, Object> details) {
        this.actionType = actionType;
        this.summary = summary;
        this.details = details;
    }

    public String getActionType() {
        return actionType;
    }

    public String getSummary() {
        return summary;
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}
