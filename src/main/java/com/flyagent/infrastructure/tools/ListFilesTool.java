package com.flyagent.infrastructure.tools;

import com.flyagent.common.exception.SecurityException;
import com.flyagent.domain.tool.*;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

/**
 * 文件列表工具。
 *
 * <p>以 ASCII 树形结构展示目录内容，支持深度控制和隐藏文件过滤。
 * 自动忽略常见构建和版本控制目录。</p>
 *
 * <h3>参数</h3>
 * <ul>
 *   <li>{@code path} — 目录路径（相对于 workspace），默认 "."</li>
 *   <li>{@code maxDepth} — 最大递归深度（1~10），默认 3</li>
 *   <li>{@code includeHidden} — 是否包含隐藏文件/目录，默认 false</li>
 * </ul>
 *
 * @author FlyAgent Team
 * @since 1.0
 */
public class ListFilesTool implements Tool {

    private static final String DEFAULT_PATH = ".";
    private static final int DEFAULT_MAX_DEPTH = 3;
    private static final int MIN_DEPTH = 1;
    private static final int MAX_DEPTH = 10;
    private static final int MAX_OUTPUT_CHARS = 30000;

    /** 默认忽略的目录名集合。 */
    private static final Set<String> DEFAULT_IGNORED_DIRS = Set.of(
            ".git", "target", "node_modules", ".idea", "__pycache__"
    );

    @Override
    public String name() {
        return "list_files";
    }

    @Override
    public String description() {
        return "List files and directories in a tree format within the workspace. "
                + "Supports depth control and hidden file filtering. "
                + "Automatically skips common build and VCS directories.";
    }

    @Override
    public ToolDefinition definition() {
        Map<String, JsonSchemaProperty> properties = new LinkedHashMap<>();
        properties.put("path", new JsonSchemaProperty("string",
                "Relative path to the directory to list (default: \"" + DEFAULT_PATH + "\")"));
        properties.put("maxDepth", new JsonSchemaProperty("integer",
                "Maximum recursion depth 1-" + MAX_DEPTH + " (default: " + DEFAULT_MAX_DEPTH + ")"));
        properties.put("includeHidden", new JsonSchemaProperty("boolean",
                "Whether to include hidden files and directories (default: false)"));
        ToolParameter parameters = new ToolParameter(properties, List.of());
        return new ToolDefinition(name(), description(), parameters);
    }

    @Override
    public ToolResult execute(ToolCall call, ToolExecutionContext context) {
        Map<String, Object> args = ToolArgumentParser.parseArguments(call);

        // 1. Parse arguments
        String pathStr = getStringArg(args, "path", DEFAULT_PATH);
        int maxDepth = clampDepth(getIntArg(args, "maxDepth", DEFAULT_MAX_DEPTH));
        boolean includeHidden = getBooleanArg(args, "includeHidden", false);

        // 2. Resolve and validate path
        Path safePath;
        try {
            safePath = context.getWorkspaceGuard().resolveAndValidate(pathStr);
        } catch (SecurityException e) {
            return ToolResult.error("Security violation: " + e.getMessage());
        }

        // 3. Check is directory
        if (!Files.isDirectory(safePath)) {
            return ToolResult.error("Path is not a directory: " + pathStr);
        }

        // 4. Walk file tree
        TreeBuildingFileVisitor visitor = new TreeBuildingFileVisitor(
                safePath, maxDepth, includeHidden);
        try {
            Files.walkFileTree(safePath, Collections.emptySet(), maxDepth, visitor);
        } catch (IOException e) {
            return ToolResult.error("Failed to list directory: " + pathStr + " (" + e.getMessage() + ")");
        }

        // 5. Build tree string
        String treeString = visitor.buildTreeString();
        int fileCount = visitor.getFileCount();
        int dirCount = visitor.getDirCount();

        // 6. Append summary
        String result = treeString + "\n" + fileCount + " files, " + dirCount + " directories";

        // 7. Truncate
        String truncated = TextTruncator.truncate(result, MAX_OUTPUT_CHARS, "Directory: " + pathStr);
        boolean isTruncated = result.length() > MAX_OUTPUT_CHARS;

        // 8. Return result
        String relativePath = context.getWorkspaceGuard().getWorkspaceRoot()
                .relativize(safePath).toString();

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("path", relativePath);
        metadata.put("files", fileCount);
        metadata.put("directories", dirCount);
        metadata.put("truncated", isTruncated);

        return ToolResult.success(truncated, metadata);
    }

    // ========== Helper methods ==========

    private static int clampDepth(int depth) {
        return Math.max(MIN_DEPTH, Math.min(MAX_DEPTH, depth));
    }

    private static String getStringArg(Map<String, Object> args, String key, String defaultValue) {
        Object value = args.get(key);
        if (value instanceof String s && !s.isBlank()) {
            return s;
        }
        return defaultValue;
    }

    private static int getIntArg(Map<String, Object> args, String key, int defaultValue) {
        Object value = args.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }

    private static boolean getBooleanArg(Map<String, Object> args, String key, boolean defaultValue) {
        Object value = args.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return defaultValue;
    }

    // ========== Inner class: TreeBuildingFileVisitor ==========

    /**
     * 树形文件访问器。
     *
     * <p>遍历目录树，收集文件/目录条目，并在遍历结束后构建 ASCII 树形字符串。
     * 自动跳过默认忽略目录，支持隐藏文件过滤。</p>
     */
    private static final class TreeBuildingFileVisitor extends SimpleFileVisitor<Path> {

        private final Path rootPath;
        private final int maxDepth;
        private final boolean includeHidden;
        private final List<TreeEntry> entries = new ArrayList<>();
        private int fileCount = 0;
        private int dirCount = 0;

        TreeBuildingFileVisitor(Path rootPath, int maxDepth, boolean includeHidden) {
            this.rootPath = rootPath;
            this.maxDepth = maxDepth;
            this.includeHidden = includeHidden;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
            // Skip root directory itself
            if (dir.equals(rootPath)) {
                return FileVisitResult.CONTINUE;
            }

            String name = dir.getFileName().toString();

            // Always skip default ignored directories
            if (DEFAULT_IGNORED_DIRS.contains(name)) {
                return FileVisitResult.SKIP_SUBTREE;
            }

            // Skip hidden directories when includeHidden is false
            if (!includeHidden && isHiddenName(name)) {
                return FileVisitResult.SKIP_SUBTREE;
            }

            int depth = rootPath.relativize(dir).getNameCount();
            entries.add(new TreeEntry(dir, name, true, depth));
            dirCount++;
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
            String name = file.getFileName().toString();

            // Skip hidden files when includeHidden is false
            if (!includeHidden && isHiddenName(name)) {
                return FileVisitResult.CONTINUE;
            }

            int depth = rootPath.relativize(file).getNameCount();
            entries.add(new TreeEntry(file, name, false, depth));
            fileCount++;
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc) {
            // Silently skip files/dirs that cannot be accessed
            return FileVisitResult.CONTINUE;
        }

        /**
         * 判断文件名是否为隐藏文件（以 "." 开头）。
         */
        private static boolean isHiddenName(String name) {
            return name.startsWith(".");
        }

        int getFileCount() {
            return fileCount;
        }

        int getDirCount() {
            return dirCount;
        }

        /**
         * 构建 ASCII 树形字符串。
         */
        String buildTreeString() {
            if (entries.isEmpty() && fileCount == 0 && dirCount == 0) {
                return rootPath.getFileName().toString() + "/\n(empty)";
            }

            // Group children by parent path
            Map<Path, List<TreeEntry>> childrenByParent = new LinkedHashMap<>();
            for (TreeEntry entry : entries) {
                Path parent = entry.path.getParent();
                childrenByParent.computeIfAbsent(parent, k -> new ArrayList<>()).add(entry);
            }

            // Sort children: directories first, then alphabetically by name
            for (List<TreeEntry> children : childrenByParent.values()) {
                children.sort(Comparator
                        .comparing((TreeEntry e) -> !e.isDirectory)
                        .thenComparing(e -> e.name));
            }

            // Build tree
            StringBuilder sb = new StringBuilder();
            String rootDisplay = rootPath.getFileName().toString();
            sb.append(rootDisplay).append("/\n");

            List<TreeEntry> rootChildren = childrenByParent.getOrDefault(rootPath, List.of());
            for (int i = 0; i < rootChildren.size(); i++) {
                TreeEntry child = rootChildren.get(i);
                boolean isLast = (i == rootChildren.size() - 1);
                appendEntry(child, "", isLast, childrenByParent, sb);
            }

            return sb.toString();
        }

        /**
         * 递归追加树形条目及其子节点。
         */
        private void appendEntry(TreeEntry entry, String prefix, boolean isLast,
                                 Map<Path, List<TreeEntry>> childrenByParent, StringBuilder sb) {
            String connector = isLast ? "└── " : "├── ";
            sb.append(prefix).append(connector).append(entry.name);
            if (entry.isDirectory) {
                sb.append("/");
            }
            sb.append("\n");

            String childPrefix = prefix + (isLast ? "    " : "│   ");
            List<TreeEntry> children = childrenByParent.getOrDefault(entry.path, List.of());
            for (int i = 0; i < children.size(); i++) {
                TreeEntry child = children.get(i);
                boolean childIsLast = (i == children.size() - 1);
                appendEntry(child, childPrefix, childIsLast, childrenByParent, sb);
            }
        }
    }

    // ========== Inner class: TreeEntry ==========

    /**
     * 树形条目，表示目录中的一个文件或子目录。
     */
    private static final class TreeEntry {
        final Path path;
        final String name;
        final boolean isDirectory;
        final int depth;

        TreeEntry(Path path, String name, boolean isDirectory, int depth) {
            this.path = path;
            this.name = name;
            this.isDirectory = isDirectory;
            this.depth = depth;
        }
    }
}
