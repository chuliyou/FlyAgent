package com.flyagent.domain.tool;

import com.flyagent.common.exception.SecurityException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Workspace 路径安全守卫。
 * <p>
 * 负责解析用户提供的相对路径并验证其在许可的 workspace 范围内，
 * 同时检测并拒绝符号链接逃逸。
 *
 * @author FlyAgent Team
 * @since 1.0
 */
public class WorkspaceGuard {

    private final Path workspaceRoot;

    /**
     * 构造并规范化 workspace 根路径。
     *
     * @param workspaceRoot workspace 的根路径（将自动转换为绝对路径并规范化）
     */
    public WorkspaceGuard(Path workspaceRoot) {
        this.workspaceRoot = workspaceRoot.toAbsolutePath().normalize();
    }

    /**
     * 解析相对路径并校验安全性。
     *
     * @param relativePath 用户提供的相对路径
     * @return 安全的绝对路径
     * @throws SecurityException 当路径在 workspace 之外时
     */
    public Path resolveAndValidate(String relativePath) {
        Path resolved = workspaceRoot.resolve(relativePath).normalize();
        validate(resolved);
        try {
            if (Files.isSymbolicLink(resolved)) {
                Path realPath = resolved.toRealPath();
                if (!realPath.startsWith(workspaceRoot)) {
                    throw new SecurityException(
                            "Symbolic link resolves outside workspace: " + relativePath);
                }
            }
        } catch (IOException e) {
            if (!resolved.startsWith(workspaceRoot)) {
                throw new SecurityException("Path is outside workspace: " + relativePath);
            }
        }
        return resolved;
    }

    /**
     * 校验已解析的路径是否在 workspace 内。
     *
     * @param path 待校验的路径
     * @throws SecurityException 当路径逃逸到 workspace 之外时
     */
    public void validate(Path path) {
        Path normalized = path.normalize();
        if (!normalized.startsWith(workspaceRoot)) {
            throw new SecurityException(
                    "Path traversal detected: " + path + " is outside workspace " + workspaceRoot);
        }
    }

    /**
     * 判断是否在 workspace 内（不抛异常）。
     *
     * @param path 待判断的路径
     * @return true 如果在 workspace 内
     */
    public boolean isWithinWorkspace(Path path) {
        return path.normalize().startsWith(workspaceRoot);
    }

    /**
     * 获取 workspace 根路径。
     *
     * @return workspace 根路径
     */
    public Path getWorkspaceRoot() {
        return workspaceRoot;
    }
}
