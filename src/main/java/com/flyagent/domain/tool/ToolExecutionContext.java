package com.flyagent.domain.tool;

import java.nio.file.Path;
import java.time.Duration;

/**
 * 工具执行上下文值对象。
 *
 * <p>封装工具执行所需的全部运行时环境信息。
 * 通过 Builder 模式构造，提供合理的默认值。</p>
 *
 * @author FlyAgent Team
 * @since 1.0
 */
public class ToolExecutionContext {

    private final Path workspace;
    private final Duration timeout;
    private final boolean requireShellApproval;
    private final boolean requireWriteApproval;
    private final ApprovalHandler approvalHandler;
    private final WorkspaceGuard workspaceGuard;

    private ToolExecutionContext(Builder builder) {
        this.workspace = builder.workspace;
        this.timeout = builder.timeout;
        this.requireShellApproval = builder.requireShellApproval;
        this.requireWriteApproval = builder.requireWriteApproval;
        this.approvalHandler = builder.approvalHandler;
        this.workspaceGuard = builder.workspaceGuard;
    }

    public Path getWorkspace() {
        return workspace;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public boolean isRequireShellApproval() {
        return requireShellApproval;
    }

    public boolean isRequireWriteApproval() {
        return requireWriteApproval;
    }

    public ApprovalHandler getApprovalHandler() {
        return approvalHandler;
    }

    public WorkspaceGuard getWorkspaceGuard() {
        return workspaceGuard;
    }

    public static Builder builder(Path workspace) {
        return new Builder(workspace);
    }

    /**
     * ToolExecutionContext 构造器。
     */
    public static class Builder {
        private final Path workspace;
        private Duration timeout = Duration.ofSeconds(120);
        private boolean requireShellApproval = true;
        private boolean requireWriteApproval = true;
        private ApprovalHandler approvalHandler;
        private WorkspaceGuard workspaceGuard;

        private Builder(Path workspace) {
            this.workspace = workspace;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder requireShellApproval(boolean v) {
            this.requireShellApproval = v;
            return this;
        }

        public Builder requireWriteApproval(boolean v) {
            this.requireWriteApproval = v;
            return this;
        }

        public Builder approvalHandler(ApprovalHandler handler) {
            this.approvalHandler = handler;
            return this;
        }

        public Builder workspaceGuard(WorkspaceGuard guard) {
            this.workspaceGuard = guard;
            return this;
        }

        public ToolExecutionContext build() {
            if (this.workspaceGuard == null) {
                this.workspaceGuard = new WorkspaceGuard(workspace);
            }
            return new ToolExecutionContext(this);
        }
    }
}
