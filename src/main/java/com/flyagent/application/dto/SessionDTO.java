package com.flyagent.application.dto;

/**
 * 会话信息 DTO。
 *
 * <p>用于 CLI 展示当前会话的基本信息。</p>
 *
 * @author FlyAgent Team
 * @since 1.0
 */
public class SessionDTO {

    private final String sessionId;
    private final String workspace;
    private final String status;
    private final String createdAt;

    public SessionDTO(String sessionId, String workspace, String status, String createdAt) {
        this.sessionId = sessionId;
        this.workspace = workspace;
        this.status = status;
        this.createdAt = createdAt;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getWorkspace() {
        return workspace;
    }

    public String getStatus() {
        return status;
    }

    public String getCreatedAt() {
        return createdAt;
    }
}
