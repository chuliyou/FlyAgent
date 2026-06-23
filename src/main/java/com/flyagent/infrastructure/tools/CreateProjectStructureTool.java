package com.flyagent.infrastructure.tools;

import com.flyagent.common.exception.SecurityException;
import com.flyagent.domain.tool.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 项目结构生成工具。
 *
 * <p>在 workspace 内创建标准项目目录结构和初始文件。
 * 当前支持 Maven Java 项目类型。</p>
 *
 * <h3>参数</h3>
 * <ul>
 *   <li>{@code projectType} — 项目类型，必填（当前仅支持 "maven-java"）</li>
 *   <li>{@code basePath} — 项目根目录（相对于 workspace），默认 "."</li>
 *   <li>{@code groupId} — Maven groupId，默认 "com.example"</li>
 *   <li>{@code artifactId} — Maven artifactId，默认 "demo"</li>
 *   <li>{@code packageName} — Java 包名，默认与 groupId 相同</li>
 *   <li>{@code overwrite} — 是否覆盖已存在文件，默认 false</li>
 * </ul>
 *
 * @author FlyAgent Team
 * @since 1.0
 */
public class CreateProjectStructureTool implements Tool {

    private static final String DEFAULT_BASE_PATH = ".";
    private static final String DEFAULT_GROUP_ID = "com.example";
    private static final String DEFAULT_ARTIFACT_ID = "demo";

    @Override
    public String name() {
        return "create_project_structure";
    }

    @Override
    public String description() {
        return "Create a standard project directory structure with initial files. "
                + "Supported project types: maven-java.";
    }

    @Override
    public ToolDefinition definition() {
        Map<String, JsonSchemaProperty> properties = new LinkedHashMap<>();
        properties.put("projectType", new JsonSchemaProperty("string",
                "Project type to create", List.of("maven-java")));
        properties.put("basePath", new JsonSchemaProperty("string",
                "Base directory for the project (default: \"" + DEFAULT_BASE_PATH + "\")"));
        properties.put("groupId", new JsonSchemaProperty("string",
                "Maven groupId (default: \"" + DEFAULT_GROUP_ID + "\")"));
        properties.put("artifactId", new JsonSchemaProperty("string",
                "Maven artifactId (default: \"" + DEFAULT_ARTIFACT_ID + "\")"));
        properties.put("packageName", new JsonSchemaProperty("string",
                "Java package name (default: same as groupId)"));
        properties.put("overwrite", new JsonSchemaProperty("boolean",
                "Whether to overwrite existing files (default: false)"));
        ToolParameter parameters = new ToolParameter(properties, List.of("projectType"));
        return new ToolDefinition(name(), description(), parameters);
    }

    @Override
    public ToolResult execute(ToolCall call, ToolExecutionContext context) {
        try {
            return doExecute(call, context);
        } catch (Exception e) {
            return ToolResult.error("Project structure creation failed: " + e.getMessage());
        }
    }

    private ToolResult doExecute(ToolCall call, ToolExecutionContext context) {
        Map<String, Object> args = ToolArgumentParser.parseArguments(call);

        // 1. 解析参数
        String projectType = (String) args.get("projectType");
        String basePath = getStringArg(args, "basePath", DEFAULT_BASE_PATH);
        String groupId = getStringArg(args, "groupId", DEFAULT_GROUP_ID);
        String artifactId = getStringArg(args, "artifactId", DEFAULT_ARTIFACT_ID);
        String packageName = getStringArg(args, "packageName", groupId);
        boolean overwrite = getBooleanArg(args, "overwrite", false);

        // 2. 校验项目类型
        if (projectType == null || projectType.isBlank()) {
            return ToolResult.error("Required parameter 'projectType' is missing");
        }
        if (!"maven-java".equals(projectType)) {
            return ToolResult.error("Unsupported project type: " + projectType
                    + ". Only 'maven-java' is currently supported.");
        }

        // 3. 解析并校验 basePath
        Path safeBasePath;
        try {
            safeBasePath = context.getWorkspaceGuard().resolveAndValidate(basePath);
        } catch (SecurityException e) {
            return ToolResult.error("Security violation: " + e.getMessage());
        }

        // 4. 构建文件列表
        LinkedHashMap<String, String> files = buildFileList(projectType, groupId, artifactId, packageName);

        // 5. 审批
        if (context.isRequireWriteApproval() && context.getApprovalHandler() != null) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("projectType", projectType);
            details.put("basePath", basePath);
            details.put("fileCount", files.size());
            ApprovalRequest approvalRequest = new ApprovalRequest(
                    "create_project_structure",
                    "Create " + projectType + " project with " + files.size()
                            + " files at: " + basePath,
                    details
            );
            if (!context.getApprovalHandler().approve(approvalRequest)) {
                return ToolResult.error("Project creation rejected by user.");
            }
        }

        // 6. 创建文件
        int createdCount = 0;
        int skippedCount = 0;
        StringBuilder summary = new StringBuilder();

        for (Map.Entry<String, String> entry : files.entrySet()) {
            String relativePath = entry.getKey();
            String content = entry.getValue();

            Path filePath = safeBasePath.resolve(relativePath);

            // 校验最终路径仍在 workspace 内
            try {
                context.getWorkspaceGuard().validate(filePath);
            } catch (SecurityException e) {
                return ToolResult.error("Security violation: " + e.getMessage());
            }

            // 检查文件是否已存在
            if (Files.exists(filePath) && !overwrite) {
                skippedCount++;
                continue;
            }

            // 创建父目录并写入文件
            try {
                Path parent = filePath.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.writeString(filePath, content, StandardCharsets.UTF_8);
                createdCount++;
                summary.append("  Created: ").append(relativePath).append("\n");
            } catch (IOException e) {
                return ToolResult.error("Failed to create file: " + relativePath
                        + " (" + e.getMessage() + ")");
            }
        }

        // 7. 构建输出
        StringBuilder output = new StringBuilder();
        output.append("Project structure created: ").append(projectType).append("\n");
        output.append("Base path: ").append(basePath).append("\n");
        output.append("Created: ").append(createdCount).append(" files\n");
        if (skippedCount > 0) {
            output.append("Skipped: ").append(skippedCount).append(" files (already exist, use overwrite=true)\n");
        }
        output.append("\n");
        output.append(summary);

        // 8. 构建元数据
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("projectType", projectType);
        metadata.put("basePath", basePath);
        metadata.put("created", createdCount);
        metadata.put("skipped", skippedCount);

        return ToolResult.success(output.toString(), metadata);
    }

    // ========== 文件模板生成 ==========

    /**
     * 构建待创建的文件列表（按创建顺序）。
     *
     * @param projectType 项目类型
     * @param groupId     Maven groupId
     * @param artifactId  Maven artifactId
     * @param packageName Java 包名
     * @return 文件相对路径到内容的映射（保持插入顺序）
     */
    private LinkedHashMap<String, String> buildFileList(String projectType,
                                                        String groupId,
                                                        String artifactId,
                                                        String packageName) {
        LinkedHashMap<String, String> files = new LinkedHashMap<>();
        String pkgPath = packageName.replace('.', '/');

        files.put("pom.xml", generatePom(groupId, artifactId));
        files.put("src/main/java/" + pkgPath + "/App.java", generateApp(packageName));
        files.put("src/test/java/" + pkgPath + "/AppTest.java", generateAppTest(packageName));
        files.put("README.md", generateReadme(artifactId));

        return files;
    }

    /**
     * 生成 pom.xml 内容。
     */
    private static String generatePom(String groupId, String artifactId) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n"
                + "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
                + "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 "
                + "http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n"
                + "    <modelVersion>4.0.0</modelVersion>\n"
                + "    <groupId>" + groupId + "</groupId>\n"
                + "    <artifactId>" + artifactId + "</artifactId>\n"
                + "    <version>1.0-SNAPSHOT</version>\n"
                + "    <packaging>jar</packaging>\n"
                + "    <properties>\n"
                + "        <maven.compiler.source>17</maven.compiler.source>\n"
                + "        <maven.compiler.target>17</maven.compiler.target>\n"
                + "        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>\n"
                + "    </properties>\n"
                + "</project>\n";
    }

    /**
     * 生成 App.java 内容。
     */
    private static String generateApp(String packageName) {
        return "package " + packageName + ";\n\n"
                + "public class App {\n"
                + "    public static void main(String[] args) {\n"
                + "        System.out.println(\"Hello, FlyAgent!\");\n"
                + "    }\n"
                + "}\n";
    }

    /**
     * 生成 AppTest.java 内容。
     */
    private static String generateAppTest(String packageName) {
        return "package " + packageName + ";\n\n"
                + "import org.junit.jupiter.api.Test;\n"
                + "import static org.junit.jupiter.api.Assertions.assertTrue;\n\n"
                + "class AppTest {\n"
                + "    @Test\n"
                + "    void shouldPass() {\n"
                + "        assertTrue(true);\n"
                + "    }\n"
                + "}\n";
    }

    /**
     * 生成 README.md 内容。
     */
    private static String generateReadme(String artifactId) {
        return "# " + artifactId + "\n\n"
                + "## Overview\n\n"
                + "A Maven Java project created by FlyAgent.\n\n"
                + "## Build\n\n"
                + "```bash\n"
                + "mvn clean compile\n"
                + "mvn test\n"
                + "```\n\n"
                + "## Run\n\n"
                + "```bash\n"
                + "mvn exec:java -Dexec.mainClass=\"App\"\n"
                + "```\n";
    }

    // ========== 参数解析辅助方法 ==========

    /**
     * 从参数 Map 中获取字符串值，不存在、为 null 或空白时返回默认值。
     */
    private static String getStringArg(Map<String, Object> args, String key, String defaultValue) {
        Object value = args.get(key);
        if (value instanceof String s && !s.isBlank()) {
            return s;
        }
        return defaultValue;
    }

    /**
     * 从参数 Map 中获取布尔值，不存在或为 null 时返回默认值。
     */
    private static boolean getBooleanArg(Map<String, Object> args, String key, boolean defaultValue) {
        Object value = args.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return defaultValue;
    }
}
