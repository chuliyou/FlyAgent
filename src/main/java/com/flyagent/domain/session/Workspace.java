package com.flyagent.domain.session;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 工作目录值对象。
 *
 * <p>封装 Agent 的工作目录路径，确保路径存在且已规范化。</p>
 *
 * @author FlyAgent Team
 * @since 1.0
 */
public class Workspace {

    private final Path path;

    /**
     * 创建工作目录。
     *
     * @param path 路径字符串
     * @throws IllegalArgumentException 当路径为空时
     */
    public Workspace(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("Workspace path must not be blank");
        }
        this.path = Path.of(path).toAbsolutePath().normalize();
    }

    public Path getPath() {
        return path;
    }

    public String getDisplayPath() {
        return path.toString();
    }

    public boolean exists() {
        return Files.exists(path);
    }

    @Override
    public String toString() {
        return path.toString();
    }
}
