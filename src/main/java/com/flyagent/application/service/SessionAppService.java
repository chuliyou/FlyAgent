package com.flyagent.application.service;

import com.flyagent.domain.session.AgentSession;
import com.flyagent.domain.session.Workspace;

/**
 * 会话应用服务。
 *
 * <p>负责会话生命周期管理：创建、关闭、状态查询。一期在内存中维护，不持久化。</p>
 *
 * @author FlyAgent Team
 * @since 1.0
 */
public class SessionAppService {

    /** 当前活跃会话 */
    private AgentSession currentSession;

    /**
     * 启动新会话。
     *
     * @param workspacePath 工作目录路径
     * @return 新创建的会话
     */
    public AgentSession startSession(String workspacePath) {
        Workspace workspace = new Workspace(workspacePath);
        this.currentSession = new AgentSession(workspace);
        return this.currentSession;
    }

    /**
     * 获取当前会话。
     *
     * @return 当前会话，未启动时返回 null
     */
    public AgentSession getCurrentSession() {
        return currentSession;
    }

    /**
     * 关闭当前会话。
     */
    public void closeSession() {
        if (currentSession != null) {
            currentSession.close();
        }
    }

    /**
     * 判断是否有活跃会话。
     *
     * @return true 表示存在活跃会话
     */
    public boolean hasActiveSession() {
        return currentSession != null && currentSession.isActive();
    }
}
