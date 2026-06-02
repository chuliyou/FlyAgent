# FlyAgent 第一次开发技术方案：项目搭建、DDD 骨架与 DeepSeek 客户端

版本：v2.0
对应总体方案：`FlyAgent一期技术方案-Claude-Code风格CLI-Agent.md`（M1 + M3 合并）
项目名称：FlyAgent
技术栈：Java 17 + Maven + DeepSeek API + OkHttp + Jackson
交付目标：完成单模块 Maven 项目骨架、四层 DDD 分层、CLI 交互循环、领域基础模型、DeepSeek Chat Completions API 客户端。

---

## 1. 方案定位

第一次开发覆盖总体方案中 **M1（工程初始化与 DDD 骨架）** 和 **M3（DeepSeek 模型调用）** 两个里程碑，是 FlyAgent 一期的起点。

本次交付后，开发者启动 CLI 可进入交互式会话，使用内置命令（`/help`、`/clear`、`/exit`、`/pwd`、`/session`），并通过 DeepSeek 客户端完成一次完整的对话请求-响应。ReAct 循环、工具执行、MySQL/Redis 持久化等留到后续迭代。

核心交付边界：

```text
┌──────────────────────────────────────────────────────┐
│  第一次开发交付（单模块，四层 DDD）                     │
│                                                      │
│  interfaces         CLI 交互 / 命令解析 / 控制台展示    │
│  application        应用服务编排 / DTO                 │
│  domain             领域模型 / 端口接口                 │
│  infrastructure     DeepSeek 客户端 / 配置管理          │
└──────────────────────────────────────────────────────┘
```

---

## 2. 项目结构

### 2.1 单模块 Maven 项目

整个项目只有一个 `pom.xml`，所有源码统一放在 `src/main/java/com/flyagent/` 下，按 DDD 四层用包名区分：

```text
FlyAgent/
├── pom.xml
├── README.md
├── prd/
│   └── 一期PRD-Claude-Code风格CLI-Agent.md
├── 技术方案/
│   ├── FlyAgent一期技术方案-Claude-Code风格CLI-Agent.md
│   └── FlyAgent第一次开发技术方案-项目搭建与DeepSeek客户端.md
└── src/
    ├── main/java/com/flyagent/
    │   ├── Main.java                            # 入口
    │   │
    │   ├── common/                              # ★ 公共层
    │   │   ├── exception/
    │   │   │   ├── FlyAgentException.java
    │   │   │   ├── ConfigException.java
    │   │   │   └── ApiException.java
    │   │   └── constant/
    │   │       └── CommonConstants.java
    │   │
    │   ├── domain/                              # ★ 领域层（不依赖 interfaces/infrastructure）
    │   │   ├── message/
    │   │   │   ├── Message.java
    │   │   │   ├── MessageRole.java
    │   │   │   ├── SystemMessage.java
    │   │   │   ├── UserMessage.java
    │   │   │   ├── AssistantMessage.java
    │   │   │   └── ToolMessage.java
    │   │   ├── tool/
    │   │   │   ├── ToolDefinition.java
    │   │   │   ├── ToolParameter.java
    │   │   │   ├── JsonSchemaProperty.java
    │   │   │   └── ToolCall.java
    │   │   ├── chat/
    │   │   │   ├── ChatRequest.java
    │   │   │   ├── ChatResponse.java
    │   │   │   ├── ChatChoice.java
    │   │   │   ├── TokenUsage.java
    │   │   │   └── ChatModelPort.java
    │   │   ├── session/
    │   │   │   ├── AgentSession.java            # 会话聚合根
    │   │   │   ├── SessionId.java               # 会话 ID 值对象
    │   │   │   ├── SessionStatus.java           # 会话状态枚举
    │   │   │   └── Workspace.java               # 工作目录值对象
    │   │   └── task/
    │   │       ├── AgentTask.java               # 任务聚合根
    │   │       ├── TaskId.java                  # 任务 ID 值对象
    │   │       └── TaskStatus.java              # 任务状态枚举
    │   │
    │   ├── application/                         # ★ 应用层
    │   │   ├── dto/
    │   │   │   ├── AgentResponseDTO.java
    │   │   │   └── SessionDTO.java
    │   │   └── service/
    │   │       ├── AgentAppService.java         # 核心应用服务
    │   │       └── SessionAppService.java       # 会话管理服务
    │   │
    │   └── infrastructure/                      # ★ 基础设施层
    │       ├── config/
    │       │   └── AppConfig.java
    │       └── deepseek/
    │           ├── DeepSeekConfig.java
    │           ├── DeepSeekHttpClient.java
    │           ├── DeepSeekResponseParser.java
    │           └── DeepSeekChatModel.java
    │
    └── test/java/com/flyagent/
        ├── domain/
        │   ├── message/
        │   │   └── MessageSerializationTest.java
        │   ├── chat/
        │   │   └── ChatRequestTest.java
        │   └── session/
        │       └── AgentSessionTest.java
        └── infrastructure/
            └── deepseek/
                ├── DeepSeekResponseParserTest.java
                └── DeepSeekChatModelIntegrationTest.java
```

### 2.2 DDD 四层依赖方向

```text
┌─────────────┐
│ interfaces  │  CLI 输入输出、命令解析、控制台渲染
└──────┬──────┘
       │ 依赖
       ▼
┌─────────────┐
│ application │  用例编排、事务管理、DTO 转换
└──────┬──────┘
       │ 依赖
       ▼
┌─────────────┐       ┌─────────────┐
│   domain    │◄──────│   common    │
│  领域核心    │       │  公共基础    │
└──────▲──────┘       └─────────────┘
       │ 实现端口
┌─────────────┐
│infrastructure│   DeepSeek、配置、文件系统
└─────────────┘
```

关键约束：**domain 包不依赖 interfaces、application、infrastructure 三个包中的任何类**。

### 2.3 包命名与职责

| 层 | 包路径 | 职责 | 依赖规则 |
| --- | --- | --- | --- |
| common | `com.flyagent.common` | 异常体系、常量 | 不依赖同项目任何包 |
| domain | `com.flyagent.domain` | 实体、值对象、聚合根、端口接口 | 只依赖 common |
| application | `com.flyagent.application` | 用例编排、DTO 组装 | 依赖 domain + common |
| interfaces | `com.flyagent.interfaces` | CLI 输入输出（本次新增） | 依赖 application + domain + common |
| infrastructure | `com.flyagent.infrastructure` | DeepSeek、配置（本次新增） | 依赖 domain + common |

---

## 3. POM 设计

### 3.1 POM 职责

`FlyAgent/pom.xml` 是项目唯一的构建文件，承担以下职责：

1. **GAV 坐标**：`com.flyagent:flyagent:1.0-SNAPSHOT`。
2. **依赖声明**：直接声明所有第三方依赖。
3. **插件配置**：compiler、surefire、shade（fat jar 打包）。

### 3.2 依赖版本清单

| 依赖 | 版本 | 用途 | 作用域 |
| --- | --- | --- | --- |
| jackson-databind | 2.18.2 | JSON 序列化/反序列化 | compile |
| jackson-annotations | 2.18.2 | Jackson 注解（多态序列化） | compile |
| okhttp | 4.12.0 | HTTP 客户端（DeepSeek API） | compile |
| slf4j-api | 2.0.16 | 日志门面 | compile |
| logback-classic | 1.5.15 | 日志实现 | runtime |
| junit-jupiter | 5.11.4 | 单元测试引擎 | test |
| mockito-core | 5.14.2 | Mock 框架 | test |
| assertj-core | 3.27.3 | 流式断言 | test |

### 3.3 完整 POM

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.flyagent</groupId>
    <artifactId>flyagent</artifactId>
    <version>1.0-SNAPSHOT</version>
    <packaging>jar</packaging>

    <name>FlyAgent</name>
    <description>DeepSeek-powered CLI coding agent</description>

    <properties>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <!-- Jackson -->
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
            <version>2.18.2</version>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-annotations</artifactId>
            <version>2.18.2</version>
        </dependency>

        <!-- HTTP Client -->
        <dependency>
            <groupId>com.squareup.okhttp3</groupId>
            <artifactId>okhttp</artifactId>
            <version>4.12.0</version>
        </dependency>

        <!-- Logging -->
        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-api</artifactId>
            <version>2.0.16</version>
        </dependency>
        <dependency>
            <groupId>ch.qos.logback</groupId>
            <artifactId>logback-classic</artifactId>
            <version>1.5.15</version>
            <scope>runtime</scope>
        </dependency>

        <!-- Testing -->
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>5.11.4</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.mockito</groupId>
            <artifactId>mockito-core</artifactId>
            <version>5.14.2</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.assertj</groupId>
            <artifactId>assertj-core</artifactId>
            <version>3.27.3</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <finalName>flyagent</finalName>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.13.0</version>
                <configuration>
                    <source>17</source>
                    <target>17</target>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.5.2</version>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-shade-plugin</artifactId>
                <version>3.6.0</version>
                <executions>
                    <execution>
                        <phase>package</phase>
                        <goals>
                            <goal>shade</goal>
                        </goals>
                        <configuration>
                            <transformers>
                                <transformer
                                    implementation="org.apache.maven.plugins.shade.resource
                                                    .ManifestResourceTransformer">
                                    <mainClass>com.flyagent.Main</mainClass>
                                </transformer>
                            </transformers>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

关键配置说明：

| 配置 | 值 | 说明 |
| --- | --- | --- |
| `<finalName>` | `flyagent` | 打包产物为 `target/flyagent.jar` |
| shade plugin | Main-Class 指向 `com.flyagent.Main` | 生成可执行 fat jar |
| surefire plugin | 3.5.2 | 测试执行引擎 |

---

## 4. 公共包设计（com.flyagent.common）

### 4.1 异常体系

采用三层异常层次：

```text
RuntimeException
  └── FlyAgentException          （基础异常）
        ├── ConfigException      （配置问题：API Key 缺失等）
        └── ApiException         （API 问题：HTTP 错误、解析失败等）
```

#### FlyAgentException

```java
package com.flyagent.common.exception;

public class FlyAgentException extends RuntimeException {
    public FlyAgentException(String message) {
        super(message);
    }

    public FlyAgentException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

#### ConfigException

```java
package com.flyagent.common.exception;

public class ConfigException extends FlyAgentException {
    public ConfigException(String message) {
        super(message);
    }
}
```

#### ApiException

```java
package com.flyagent.common.exception;

public class ApiException extends FlyAgentException {
    private final int statusCode;

    public ApiException(String message) {
        super(message);
        this.statusCode = 0;
    }

    public ApiException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 0;
    }

    public ApiException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
```

### 4.2 常量类

```java
package com.flyagent.common.constant;

public final class CommonConstants {
    private CommonConstants() {}

    public static final String DEFAULT_BASE_URL = "https://api.deepseek.com";
    public static final String CHAT_ENDPOINT = "/chat/completions";
    public static final String DEFAULT_MODEL = "deepseek-v4-pro";
    public static final String ENV_API_KEY = "DEEPSEEK_API_KEY";
}
```

---

## 5. 领域模型设计（com.flyagent.domain）

领域层是 DDD 核心，定义实体、值对象、聚合根和端口接口。**不依赖 interfaces、application、infrastructure**。

### 5.1 领域总览

第一次开发涉及的领域概念：

| 领域概念 | 类型 | 说明 | 本次实现程度 |
| --- | --- | --- | --- |
| AgentSession | 聚合根 | 一次 CLI 会话，含 workspace 和状态 | 完整实现 |
| AgentTask | 聚合根 | 一次用户任务，控制轮次和状态 | 骨架（状态流转 + 字段） |
| Message | 实体/值对象 | 多轮消息（system/user/assistant/tool） | 完整实现 |
| ToolDefinition | 值对象 | 工具描述定义 | 完整实现 |
| ToolCall | 值对象 | 模型返回的工具调用 | 完整实现 |
| ChatRequest/Response | 值对象 | 模型请求/响应 | 完整实现 |
| ChatModelPort | 端口接口 | 模型调用抽象 | 完整实现 |

### 5.2 AgentSession 聚合根

```java
package com.flyagent.domain.session;

import java.time.LocalDateTime;

public class AgentSession {
    private final SessionId id;
    private final Workspace workspace;
    private SessionStatus status;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime closedAt;

    public AgentSession(Workspace workspace) {
        this.id = SessionId.generate();
        this.workspace = workspace;
        this.status = SessionStatus.ACTIVE;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    // 行为方法
    public void activate() {
        if (this.status == SessionStatus.CLOSED) {
            throw new IllegalStateException("Cannot activate a closed session");
        }
        this.status = SessionStatus.ACTIVE;
        this.updatedAt = LocalDateTime.now();
    }

    public void close() {
        this.status = SessionStatus.CLOSED;
        this.closedAt = LocalDateTime.now();
        this.updatedAt = this.closedAt;
    }

    public boolean isActive() {
        return this.status == SessionStatus.ACTIVE;
    }

    // getters ...
    public SessionId getId()           { return id; }
    public Workspace getWorkspace()    { return workspace; }
    public SessionStatus getStatus()   { return status; }
    public LocalDateTime getCreatedAt(){ return createdAt; }
    public LocalDateTime getUpdatedAt(){ return updatedAt; }
    public LocalDateTime getClosedAt() { return closedAt; }
}
```

### 5.3 SessionId 值对象

```java
package com.flyagent.domain.session;

import java.util.UUID;

public class SessionId {
    private final String value;

    private SessionId(String value) {
        this.value = value;
    }

    public static SessionId generate() {
        return new SessionId("ses_" + UUID.randomUUID().toString().substring(0, 8));
    }

    public static SessionId of(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("SessionId must not be blank");
        }
        return new SessionId(value);
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SessionId)) return false;
        return value.equals(((SessionId) o).value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}
```

### 5.4 SessionStatus 枚举

```java
package com.flyagent.domain.session;

public enum SessionStatus {
    ACTIVE,
    CLOSED
}
```

### 5.5 Workspace 值对象

```java
package com.flyagent.domain.session;

import java.nio.file.Path;

public class Workspace {
    private final Path path;

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

    @Override
    public String toString() {
        return path.toString();
    }
}
```

### 5.6 AgentTask 聚合根（骨架）

```java
package com.flyagent.domain.task;

import com.flyagent.domain.session.SessionId;
import java.time.LocalDateTime;

public class AgentTask {
    private final TaskId id;
    private final SessionId sessionId;
    private final String userInstruction;
    private TaskStatus status;
    private final int maxTurns;
    private int currentTurn;
    private String finalAnswer;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public AgentTask(SessionId sessionId, String userInstruction, int maxTurns) {
        this.id = TaskId.generate();
        this.sessionId = sessionId;
        this.userInstruction = userInstruction;
        this.status = TaskStatus.CREATED;
        this.maxTurns = maxTurns;
        this.currentTurn = 0;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    // 行为方法
    public void start() {
        if (this.status != TaskStatus.CREATED) {
            throw new IllegalStateException("Task can only start from CREATED status");
        }
        this.status = TaskStatus.RUNNING;
        this.updatedAt = LocalDateTime.now();
    }

    public void increaseTurn() {
        this.currentTurn++;
        this.updatedAt = LocalDateTime.now();
    }

    public boolean canContinue() {
        return this.status == TaskStatus.RUNNING && this.currentTurn < this.maxTurns;
    }

    public void complete(String finalAnswer) {
        this.finalAnswer = finalAnswer;
        this.status = TaskStatus.COMPLETED;
        this.updatedAt = LocalDateTime.now();
    }

    public void fail(String reason) {
        this.finalAnswer = reason;
        this.status = TaskStatus.FAILED;
        this.updatedAt = LocalDateTime.now();
    }

    public void hitMaxTurns() {
        this.status = TaskStatus.HIT_MAX_TURNS;
        this.updatedAt = LocalDateTime.now();
    }

    // getters ...
    public TaskId getId()                 { return id; }
    public SessionId getSessionId()       { return sessionId; }
    public String getUserInstruction()    { return userInstruction; }
    public TaskStatus getStatus()         { return status; }
    public int getMaxTurns()              { return maxTurns; }
    public int getCurrentTurn()           { return currentTurn; }
    public String getFinalAnswer()        { return finalAnswer; }
    public LocalDateTime getCreatedAt()   { return createdAt; }
    public LocalDateTime getUpdatedAt()   { return updatedAt; }
}
```

### 5.7 TaskId 值对象

```java
package com.flyagent.domain.task;

import java.util.UUID;

public class TaskId {
    private final String value;

    private TaskId(String value) {
        this.value = value;
    }

    public static TaskId generate() {
        return new TaskId("task_" + UUID.randomUUID().toString().substring(0, 8));
    }

    public static TaskId of(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("TaskId must not be blank");
        }
        return new TaskId(value);
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TaskId)) return false;
        return value.equals(((TaskId) o).value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}
```

### 5.8 TaskStatus 枚举

```java
package com.flyagent.domain.task;

public enum TaskStatus {
    CREATED,
    RUNNING,
    WAITING_APPROVAL,
    COMPLETED,
    FAILED,
    HIT_MAX_TURNS,
    REJECTED
}
```

一期只用到 `CREATED`、`RUNNING`、`COMPLETED`、`FAILED` 状态。其余状态在后续工具执行和审批模块中启用。

### 5.9 ChatModelPort（领域端口）

```java
package com.flyagent.domain.chat;

import com.flyagent.common.exception.ApiException;

/**
 * 聊天模型领域端口。
 * 基础设施层提供 DeepSeek 实现。
 */
public interface ChatModelPort {
    ChatResponse chat(ChatRequest request);
}
```

---

## 6. 消息与工具模型（com.flyagent.domain.message / tool / chat）

以下模型已在 v1.x 版本方案中详细定义，本次仅列出要点。完整代码参见原方案第 5-7 章。

### 6.1 消息模型（5 类）

| 类 | role | 关键字段 | 说明 |
| --- | --- | --- | --- |
| `Message` | — | `getRole()` | Jackson 多态接口，按 `role` 路由 |
| `SystemMessage` | `system` | `content` | 系统提示词 |
| `UserMessage` | `user` | `content` | 用户输入 |
| `AssistantMessage` | `assistant` | `content`, `toolCalls` | 模型回复，两种工厂方法 |
| `ToolMessage` | `tool` | `toolCallId`, `content` | 工具结果回传 |

### 6.2 工具模型（4 类）

| 类 | 说明 |
| --- | --- |
| `ToolDefinition` | 工具描述（name + description + JSON Schema parameters） |
| `ToolParameter` | 参数集合（`type: "object"` + properties + required） |
| `JsonSchemaProperty` | 单参数属性（type + description + 可选 enum） |
| `ToolCall` | 模型返回的调用意图（id + FunctionCall） |

### 6.3 Chat 模型（4 类 + 1 端口）

| 类 | 说明 |
| --- | --- |
| `ChatRequest` | Builder 模式，默认 model=`deepseek-v4-pro`, thinking=enabled |
| `ChatResponse` | `id` + `model` + `choices` + `usage` |
| `ChatChoice` | `index` + `message`(AssistantMessage) + `finishReason` |
| `TokenUsage` | `promptTokens` + `completionTokens` + `totalTokens` |
| `ChatModelPort` | 领域端口接口，`ChatResponse chat(ChatRequest)` |

---

## 7. 应用层设计（com.flyagent.application）

### 7.1 设计思路

应用层负责用例编排，不包含业务规则。本次实现两个应用服务：

| 服务 | 职责 |
| --- | --- |
| `SessionAppService` | 会话生命周期管理（创建、关闭、状态查询） |
| `AgentAppService` | 用户输入处理编排（组装请求 → 调用模型 → 返回结果） |

### 7.2 SessionAppService

```java
package com.flyagent.application.service;

import com.flyagent.domain.session.AgentSession;
import com.flyagent.domain.session.Workspace;

public class SessionAppService {
    private AgentSession currentSession;

    public AgentSession startSession(String workspacePath) {
        Workspace workspace = new Workspace(workspacePath);
        this.currentSession = new AgentSession(workspace);
        return this.currentSession;
    }

    public AgentSession getCurrentSession() {
        return currentSession;
    }

    public void closeSession() {
        if (currentSession != null) {
            currentSession.close();
        }
    }

    public boolean hasActiveSession() {
        return currentSession != null && currentSession.isActive();
    }
}
```

一期会话管理在内存中维护，不持久化。后续迭代接入 MySQL/Redis。

### 7.3 AgentAppService

```java
package com.flyagent.application.service;

import com.flyagent.application.dto.AgentResponseDTO;
import com.flyagent.domain.chat.*;
import com.flyagent.domain.message.*;
import com.flyagent.domain.session.AgentSession;
import java.util.List;

public class AgentAppService {
    private final ChatModelPort chatModel;
    private final SessionAppService sessionService;

    public AgentAppService(ChatModelPort chatModel, SessionAppService sessionService) {
        this.chatModel = chatModel;
        this.sessionService = sessionService;
    }

    public AgentResponseDTO handleUserInput(String userInput) {
        if (!sessionService.hasActiveSession()) {
            return AgentResponseDTO.error("No active session. Please start a session first.");
        }

        AgentSession session = sessionService.getCurrentSession();

        // 1. 构造消息列表（一期：单轮，无历史）
        UserMessage userMessage = new UserMessage(userInput);
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(userMessage))
                .build();

        // 2. 调用模型
        ChatResponse response = chatModel.chat(request);
        ChatChoice firstChoice = response.firstChoice();

        if (firstChoice == null) {
            return AgentResponseDTO.error("No response from model");
        }

        // 3. 构建 DTO
        AssistantMessage assistantMsg = firstChoice.getMessage();
        String content = assistantMsg.hasContent() ? assistantMsg.getContent() : "";
        boolean hasToolCalls = assistantMsg.hasToolCalls();

        return AgentResponseDTO.success(
                content,
                hasToolCalls,
                response.getUsage()
        );
    }
}
```

### 7.4 DTO

#### AgentResponseDTO

```java
package com.flyagent.application.dto;

import com.flyagent.domain.chat.TokenUsage;

public class AgentResponseDTO {
    private final boolean success;
    private final String content;
    private final boolean hasToolCalls;
    private final TokenUsage tokenUsage;
    private final String errorMessage;

    private AgentResponseDTO(boolean success, String content, boolean hasToolCalls,
                              TokenUsage tokenUsage, String errorMessage) {
        this.success = success;
        this.content = content;
        this.hasToolCalls = hasToolCalls;
        this.tokenUsage = tokenUsage;
        this.errorMessage = errorMessage;
    }

    public static AgentResponseDTO success(String content, boolean hasToolCalls,
                                            TokenUsage usage) {
        return new AgentResponseDTO(true, content, hasToolCalls, usage, null);
    }

    public static AgentResponseDTO error(String errorMessage) {
        return new AgentResponseDTO(false, null, false, null, errorMessage);
    }

    public boolean isSuccess()       { return success; }
    public String getContent()       { return content; }
    public boolean hasToolCalls()    { return hasToolCalls; }
    public TokenUsage getTokenUsage(){ return tokenUsage; }
    public String getErrorMessage()  { return errorMessage; }
}
```

#### SessionDTO

```java
package com.flyagent.application.dto;

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

    public String getSessionId() { return sessionId; }
    public String getWorkspace() { return workspace; }
    public String getStatus()    { return status; }
    public String getCreatedAt() { return createdAt; }
}
```

---

## 8. CLI 交互设计（com.flyagent.interfaces）

### 8.1 设计思路

CLI 层负责用户输入输出，通过 `Scanner` 读取、`System.out` 输出，不依赖第三方 CLI 框架。

核心类：

| 类 | 职责 |
| --- | --- |
| `Main` | 入口，组装依赖并启动 CLI |
| `AgentCli` | CLI 主循环，分发内置命令和用户输入 |
| `CommandParser` | 解析输入，区分内置命令与自然语言 |
| `ConsolePresenter` | 格式化输出（欢迎信息、结果、错误） |

### 8.2 Main 入口

```java
package com.flyagent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flyagent.application.service.AgentAppService;
import com.flyagent.application.service.SessionAppService;
import com.flyagent.domain.chat.ChatModelPort;
import com.flyagent.infrastructure.config.AppConfig;
import com.flyagent.infrastructure.deepseek.DeepSeekChatModel;
import com.flyagent.infrastructure.deepseek.DeepSeekConfig;
import com.flyagent.interfaces.cli.AgentCli;

public class Main {
    public static void main(String[] args) {
        // 1. 加载配置
        AppConfig appConfig = new AppConfig();
        DeepSeekConfig dsConfig = appConfig.getDeepSeekConfig();

        // 2. 初始化基础设施
        ObjectMapper objectMapper = new ObjectMapper();
        ChatModelPort chatModel = new DeepSeekChatModel(dsConfig, objectMapper);

        // 3. 初始化应用服务
        SessionAppService sessionService = new SessionAppService();
        AgentAppService agentService = new AgentAppService(chatModel, sessionService);

        // 4. 解析命令行参数
        String workspace = args.length > 0 ? args[0] : System.getProperty("user.dir");

        // 5. 启动 CLI
        AgentCli cli = new AgentCli(agentService, sessionService, dsConfig, workspace);
        cli.start();
    }
}
```

### 8.3 CommandParser

```java
package com.flyagent.interfaces.cli;

public class CommandParser {

    public static ParsedCommand parse(String input) {
        if (input == null || input.isBlank()) {
            return ParsedCommand.empty();
        }

        String trimmed = input.trim();

        // 内置命令
        if (trimmed.equals("/help") || trimmed.equalsIgnoreCase("help")) {
            return ParsedCommand.builtin(BuiltinCommand.HELP);
        }
        if (trimmed.equals("/clear") || trimmed.equalsIgnoreCase("clear")) {
            return ParsedCommand.builtin(BuiltinCommand.CLEAR);
        }
        if (trimmed.equals("/exit") || trimmed.equalsIgnoreCase("exit")
                || trimmed.equalsIgnoreCase("quit")) {
            return ParsedCommand.builtin(BuiltinCommand.EXIT);
        }
        if (trimmed.equals("/pwd") || trimmed.equalsIgnoreCase("pwd")) {
            return ParsedCommand.builtin(BuiltinCommand.PWD);
        }
        if (trimmed.equals("/session") || trimmed.equalsIgnoreCase("/session")) {
            return ParsedCommand.builtin(BuiltinCommand.SESSION);
        }

        // 默认：自然语言用户输入
        return ParsedCommand.userInput(trimmed);
    }
}
```

### 8.4 ParsedCommand

```java
package com.flyagent.interfaces.cli;

public class ParsedCommand {
    private final CommandType type;
    private final BuiltinCommand builtinCommand;
    private final String userInput;

    private ParsedCommand(CommandType type, BuiltinCommand builtinCommand, String userInput) {
        this.type = type;
        this.builtinCommand = builtinCommand;
        this.userInput = userInput;
    }

    public static ParsedCommand builtin(BuiltinCommand cmd) {
        return new ParsedCommand(CommandType.BUILTIN, cmd, null);
    }

    public static ParsedCommand userInput(String input) {
        return new ParsedCommand(CommandType.USER_INPUT, null, input);
    }

    public static ParsedCommand empty() {
        return new ParsedCommand(CommandType.EMPTY, null, null);
    }

    public CommandType getType()             { return type; }
    public BuiltinCommand getBuiltinCommand(){ return builtinCommand; }
    public String getUserInput()             { return userInput; }
    public boolean isEmpty()                 { return type == CommandType.EMPTY; }

    public enum CommandType {
        EMPTY, BUILTIN, USER_INPUT
    }
}
```

### 8.5 BuiltinCommand 枚举

```java
package com.flyagent.interfaces.cli;

public enum BuiltinCommand {
    HELP,
    CLEAR,
    EXIT,
    PWD,
    SESSION
}
```

### 8.6 ConsolePresenter

```java
package com.flyagent.interfaces.cli;

import com.flyagent.application.dto.AgentResponseDTO;
import com.flyagent.application.dto.SessionDTO;
import com.flyagent.domain.session.AgentSession;
import com.flyagent.infrastructure.deepseek.DeepSeekConfig;

public class ConsolePresenter {

    public void printWelcome(AgentSession session, DeepSeekConfig config) {
        System.out.println("FlyAgent started.");
        System.out.println("Provider: DeepSeek");
        System.out.println("Model: " + config.getDefaultModel());
        System.out.println("Workspace: " + session.getWorkspace().getDisplayPath());
        System.out.println("Session: " + session.getId().getValue());
        System.out.println("Type /help for commands.");
        System.out.println();
    }

    public void printPrompt() {
        System.out.print("> ");
    }

    public void printHelp() {
        System.out.println();
        System.out.println("Available commands:");
        System.out.println("  /help      Show this help");
        System.out.println("  /clear     Clear current session context");
        System.out.println("  /exit      Exit FlyAgent");
        System.out.println("  /pwd       Show current workspace");
        System.out.println("  /session   Show current session info");
        System.out.println();
        System.out.println("Or just type your coding task in natural language.");
        System.out.println();
    }

    public void printSessionInfo(SessionDTO dto) {
        System.out.println();
        System.out.println("Session ID: " + dto.getSessionId());
        System.out.println("Workspace:  " + dto.getWorkspace());
        System.out.println("Status:     " + dto.getStatus());
        System.out.println("Created:    " + dto.getCreatedAt());
        System.out.println();
    }

    public void printPwd(String workspace) {
        System.out.println(workspace);
    }

    public void printClear() {
        System.out.println("Context cleared.");
    }

    public void printGoodbye() {
        System.out.println("Goodbye!");
    }

    public void printResponse(AgentResponseDTO response) {
        if (response.isSuccess()) {
            System.out.println();
            System.out.println(response.getContent());
            if (response.getTokenUsage() != null) {
                System.out.println();
                System.out.println("[Tokens: "
                        + response.getTokenUsage().getPromptTokens() + " in / "
                        + response.getTokenUsage().getCompletionTokens() + " out]");
            }
        } else {
            System.out.println();
            System.out.println("Error: " + response.getErrorMessage());
        }
        System.out.println();
    }

    public void printApiError(String message) {
        System.out.println();
        System.out.println("Error: " + message);
        System.out.println();
    }
}
```

### 8.7 AgentCli 主循环

```java
package com.flyagent.interfaces.cli;

import com.flyagent.application.dto.AgentResponseDTO;
import com.flyagent.application.dto.SessionDTO;
import com.flyagent.application.service.AgentAppService;
import com.flyagent.application.service.SessionAppService;
import com.flyagent.common.exception.ApiException;
import com.flyagent.common.exception.ConfigException;
import com.flyagent.domain.session.AgentSession;
import com.flyagent.infrastructure.deepseek.DeepSeekConfig;
import java.util.Scanner;

public class AgentCli {
    private final AgentAppService agentService;
    private final SessionAppService sessionService;
    private final DeepSeekConfig config;
    private final String workspacePath;
    private final ConsolePresenter presenter;
    private boolean running;

    public AgentCli(AgentAppService agentService, SessionAppService sessionService,
                    DeepSeekConfig config, String workspacePath) {
        this.agentService = agentService;
        this.sessionService = sessionService;
        this.config = config;
        this.workspacePath = workspacePath;
        this.presenter = new ConsolePresenter();
        this.running = true;
    }

    public void start() {
        // 创建会话
        AgentSession session = sessionService.startSession(workspacePath);
        presenter.printWelcome(session, config);

        Scanner scanner = new Scanner(System.in);

        while (running) {
            presenter.printPrompt();
            String input = scanner.nextLine();

            ParsedCommand command = CommandParser.parse(input);

            if (command.isEmpty()) {
                continue;
            }

            switch (command.getType()) {
                case BUILTIN -> handleBuiltin(command.getBuiltinCommand());
                case USER_INPUT -> handleUserInput(command.getUserInput());
            }
        }

        scanner.close();
        sessionService.closeSession();
    }

    private void handleBuiltin(BuiltinCommand cmd) {
        switch (cmd) {
            case HELP -> presenter.printHelp();
            case CLEAR -> presenter.printClear();
            case EXIT -> {
                presenter.printGoodbye();
                running = false;
            }
            case PWD -> {
                AgentSession session = sessionService.getCurrentSession();
                if (session != null) {
                    presenter.printPwd(session.getWorkspace().getDisplayPath());
                }
            }
            case SESSION -> {
                AgentSession session = sessionService.getCurrentSession();
                if (session != null) {
                    SessionDTO dto = new SessionDTO(
                            session.getId().getValue(),
                            session.getWorkspace().getDisplayPath(),
                            session.getStatus().name(),
                            session.getCreatedAt().toString()
                    );
                    presenter.printSessionInfo(dto);
                }
            }
        }
    }

    private void handleUserInput(String input) {
        try {
            AgentResponseDTO response = agentService.handleUserInput(input);
            presenter.printResponse(response);
        } catch (ConfigException e) {
            presenter.printApiError(e.getMessage());
        } catch (ApiException e) {
            presenter.printApiError("API error [" + e.getStatusCode() + "]: " + e.getMessage());
        } catch (Exception e) {
            presenter.printApiError("Unexpected error: " + e.getMessage());
        }
    }
}
```

### 8.8 CLI 启动与运行效果

启动命令：

```bash
java -jar target/flyagent.jar
```

或指定 workspace：

```bash
java -jar target/flyagent.jar /path/to/project
```

预期启动输出：

```text
FlyAgent started.
Provider: DeepSeek
Model: deepseek-v4-pro
Workspace: J:\code\FlyAgent
Session: ses_a1b2c3d4
Type /help for commands.

>
```

### 8.9 内置命令功能

| 命令 | 效果 |
| --- | --- |
| `/help` 或 `help` | 显示所有可用命令及说明 |
| `/clear` 或 `clear` | 清空当前会话上下文（一期输出提示，不实际执行） |
| `/exit`、`exit`、`quit` | 关闭会话并退出 |
| `/pwd` 或 `pwd` | 显示当前 workspace 绝对路径 |
| `/session` | 显示会话 ID、workspace、状态、创建时间 |

输入其他文本直接作为自然语言任务发送给 DeepSeek 模型。

---

## 9. DeepSeek 客户端实现（com.flyagent.infrastructure.deepseek）

### 9.1 组件拆分

| 类 | 职责 |
| --- | --- |
| `DeepSeekConfig` | 封装 API 连接参数（URL、Key、超时） |
| `DeepSeekHttpClient` | HTTP 请求：序列化 ChatRequest → JSON → POST → 返回原始响应 |
| `DeepSeekResponseParser` | 响应解析：JSON 字符串 → `ChatResponse` |
| `DeepSeekChatModel` | 实现 `ChatModelPort`，串联校验 → 请求 → 解析 |

### 9.2 DeepSeekConfig

```java
package com.flyagent.infrastructure.deepseek;

import com.flyagent.common.constant.CommonConstants;

public class DeepSeekConfig {
    private final String baseUrl;
    private final String apiKey;
    private final String defaultModel;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;

    public DeepSeekConfig(String baseUrl, String apiKey, String defaultModel) {
        this(baseUrl, apiKey, defaultModel, 30_000, 120_000);
    }

    public DeepSeekConfig(String baseUrl, String apiKey, String defaultModel,
                           int connectTimeoutMs, int readTimeoutMs) {
        this.baseUrl = baseUrl != null ? baseUrl : CommonConstants.DEFAULT_BASE_URL;
        this.apiKey = apiKey;
        this.defaultModel = defaultModel != null ? defaultModel : CommonConstants.DEFAULT_MODEL;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
    }

    public String getBaseUrl()            { return baseUrl; }
    public String getApiKey()             { return apiKey; }
    public String getDefaultModel()       { return defaultModel; }
    public int getConnectTimeoutMs()      { return connectTimeoutMs; }
    public int getReadTimeoutMs()         { return readTimeoutMs; }
    public String getChatEndpoint()       { return baseUrl + CommonConstants.CHAT_ENDPOINT; }
}
```

### 9.3 DeepSeekHttpClient

基于 OkHttp 发送同步 POST 请求：

```java
package com.flyagent.infrastructure.deepseek;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flyagent.common.exception.ApiException;
import com.flyagent.domain.chat.ChatRequest;
import okhttp3.*;
import java.io.IOException;
import java.time.Duration;
import java.util.*;

public class DeepSeekHttpClient {
    private static final MediaType JSON_MEDIA_TYPE =
            MediaType.parse("application/json; charset=utf-8");

    private final DeepSeekConfig config;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public DeepSeekHttpClient(DeepSeekConfig config, ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofMillis(config.getConnectTimeoutMs()))
                .readTimeout(Duration.ofMillis(config.getReadTimeoutMs()))
                .build();
    }

    public String postChatRequest(ChatRequest request) throws ApiException {
        Map<String, Object> body = buildRequestBody(request);
        String json;
        try {
            json = objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new ApiException("Failed to serialize ChatRequest", e);
        }

        Request httpRequest = new Request.Builder()
                .url(config.getChatEndpoint())
                .header("Authorization", "Bearer " + config.getApiKey())
                .header("Content-Type", "application/json; charset=utf-8")
                .post(RequestBody.create(json, JSON_MEDIA_TYPE))
                .build();

        try (Response response = httpClient.newCall(httpRequest).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "";
                throw new ApiException(response.code(),
                        "DeepSeek API returned " + response.code() + ": " + errorBody);
            }
            return response.body().string();
        } catch (ApiException e) {
            throw e;
        } catch (IOException e) {
            throw new ApiException("Failed to call DeepSeek API: " + e.getMessage(), e);
        }
    }

    Map<String, Object> buildRequestBody(ChatRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", resolveModel(request));
        body.put("messages", request.getMessages());
        body.put("stream", false);

        if (request.isThinking()) {
            body.put("thinking", Map.of("type", "enabled"));
        }
        if (request.getReasoningEffort() != null && !request.getReasoningEffort().isEmpty()) {
            body.put("reasoning_effort", request.getReasoningEffort());
        }
        if (request.getTools() != null && !request.getTools().isEmpty()) {
            body.put("tools", request.getTools());
        }
        return body;
    }

    private String resolveModel(ChatRequest request) {
        String reqModel = request.getModel();
        return (reqModel != null && !reqModel.isEmpty()) ? reqModel : config.getDefaultModel();
    }
}
```

### 9.4 DeepSeekResponseParser

```java
package com.flyagent.infrastructure.deepseek;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flyagent.common.exception.ApiException;
import com.flyagent.domain.chat.*;
import com.flyagent.domain.message.AssistantMessage;
import com.flyagent.domain.tool.ToolCall;
import java.util.*;

public class DeepSeekResponseParser {
    private final ObjectMapper objectMapper;

    public DeepSeekResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ChatResponse parse(String responseJson) {
        try {
            JsonNode root = objectMapper.readTree(responseJson);

            String id = root.path("id").asText();
            String model = root.path("model").asText();

            List<ChatChoice> choices = new ArrayList<>();
            for (JsonNode choiceNode : root.path("choices")) {
                int index = choiceNode.path("index").asInt();
                String finishReason = choiceNode.path("finish_reason").asText(null);

                JsonNode msgNode = choiceNode.path("message");
                String content = msgNode.has("content") && !msgNode.get("content").isNull()
                        ? msgNode.get("content").asText() : null;

                List<ToolCall> toolCalls = parseToolCalls(msgNode);

                AssistantMessage message = (toolCalls != null)
                        ? AssistantMessage.ofToolCalls(toolCalls)
                        : AssistantMessage.ofText(content);

                choices.add(new ChatChoice(index, message, finishReason));
            }

            JsonNode usageNode = root.path("usage");
            TokenUsage usage = new TokenUsage(
                    usageNode.path("prompt_tokens").asInt(),
                    usageNode.path("completion_tokens").asInt(),
                    usageNode.path("total_tokens").asInt()
            );

            return new ChatResponse(id, model, choices, usage);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException("Failed to parse DeepSeek response", e);
        }
    }

    private List<ToolCall> parseToolCalls(JsonNode msgNode) {
        JsonNode tcNode = msgNode.path("tool_calls");
        if (!tcNode.isArray() || tcNode.isEmpty()) return null;
        List<ToolCall> toolCalls = new ArrayList<>();
        for (JsonNode tc : tcNode) {
            try {
                toolCalls.add(objectMapper.treeToValue(tc, ToolCall.class));
            } catch (Exception e) {
                throw new ApiException("Failed to parse tool_call", e);
            }
        }
        return toolCalls;
    }
}
```

### 9.5 DeepSeekChatModel

```java
package com.flyagent.infrastructure.deepseek;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flyagent.common.exception.ConfigException;
import com.flyagent.domain.chat.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeepSeekChatModel implements ChatModelPort {
    private static final Logger log = LoggerFactory.getLogger(DeepSeekChatModel.class);

    private final DeepSeekConfig config;
    private final DeepSeekHttpClient httpClient;
    private final DeepSeekResponseParser parser;

    public DeepSeekChatModel(DeepSeekConfig config, ObjectMapper objectMapper) {
        this.config = config;
        this.httpClient = new DeepSeekHttpClient(config, objectMapper);
        this.parser = new DeepSeekResponseParser(objectMapper);
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        if (config.getApiKey() == null || config.getApiKey().isBlank()) {
            throw new ConfigException(
                    "DeepSeek API Key not set. Set DEEPSEEK_API_KEY environment variable.");
        }

        ChatRequest resolvedRequest = resolveModel(request);

        log.debug("Sending chat request: model={}, messages={}, tools={}",
                resolvedRequest.getModel(),
                resolvedRequest.getMessages().size(),
                resolvedRequest.getTools() != null ? resolvedRequest.getTools().size() : 0);

        long start = System.currentTimeMillis();
        String responseJson = httpClient.postChatRequest(resolvedRequest);
        long latencyMs = System.currentTimeMillis() - start;

        ChatResponse response = parser.parse(responseJson);

        if (response.getUsage() != null) {
            log.info("Chat completed: model={}, latencyMs={}, "
                            + "promptTokens={}, completionTokens={}, totalTokens={}",
                    response.getModel(), latencyMs,
                    response.getUsage().getPromptTokens(),
                    response.getUsage().getCompletionTokens(),
                    response.getUsage().getTotalTokens());
        }

        return response;
    }

    private ChatRequest resolveModel(ChatRequest request) {
        if (request.getModel() != null && !request.getModel().isEmpty()) return request;
        return ChatRequest.builder()
                .model(config.getDefaultModel())
                .messages(request.getMessages())
                .tools(request.getTools())
                .thinking(request.isThinking())
                .reasoningEffort(request.getReasoningEffort())
                .stream(request.isStream())
                .build();
    }
}
```

---

## 10. 配置管理

### 10.1 AppConfig

```java
package com.flyagent.infrastructure.config;

import com.flyagent.common.constant.CommonConstants;
import com.flyagent.infrastructure.deepseek.DeepSeekConfig;

public class AppConfig {
    private final DeepSeekConfig deepSeekConfig;

    public AppConfig() {
        String apiKey = System.getenv(CommonConstants.ENV_API_KEY);
        String baseUrl = System.getenv().getOrDefault(
                "DEEPSEEK_BASE_URL", CommonConstants.DEFAULT_BASE_URL);
        String model = System.getenv().getOrDefault(
                "DEEPSEEK_MODEL", CommonConstants.DEFAULT_MODEL);
        this.deepSeekConfig = new DeepSeekConfig(baseUrl, apiKey, model);
    }

    public DeepSeekConfig getDeepSeekConfig() {
        return deepSeekConfig;
    }
}
```

### 10.2 配置项映射

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `DEEPSEEK_API_KEY` | 无（必填） | DeepSeek API 密钥 |
| `DEEPSEEK_BASE_URL` | `https://api.deepseek.com` | API 基础地址 |
| `DEEPSEEK_MODEL` | `deepseek-v4-pro` | 默认模型名 |

---

## 11. 开发任务清单

| 序号 | 任务 | 涉及包 | 产出物 | 优先级 |
| --- | --- | --- | --- | --- |
| T1 | 创建项目目录与 POM | 全部 | 单模块 Maven 项目，含 shade 插件配置 | P0 |
| T2 | 实现公共包（异常 + 常量） | common | 3 个异常类 + 1 个常量类 | P0 |
| T3 | 实现 MessageRole 和 Message 接口 | domain/message | 枚举 + 接口 + Jackson 多态配置 | P0 |
| T4 | 实现四种消息类 | domain/message | SystemMessage, UserMessage, AssistantMessage, ToolMessage | P0 |
| T5 | 实现工具领域类 | domain/tool | ToolDefinition, ToolParameter, JsonSchemaProperty, ToolCall | P0 |
| T6 | 实现 Chat 请求/响应模型 + ChatModelPort | domain/chat | ChatRequest(Builder), ChatResponse, ChatChoice, TokenUsage, ChatModelPort | P0 |
| T7 | 实现 AgentSession 聚合根及值对象 | domain/session | AgentSession, SessionId, SessionStatus, Workspace | P0 |
| T8 | 实现 AgentTask 聚合根及值对象 | domain/task | AgentTask, TaskId, TaskStatus | P0 |
| T9 | 实现 DTO | application/dto | AgentResponseDTO, SessionDTO | P0 |
| T10 | 实现 SessionAppService | application/service | 会话生命周期管理 | P0 |
| T11 | 实现 AgentAppService | application/service | 用户输入编排 + 模型调用 | P0 |
| T12 | 实现 CommandParser + ParsedCommand + BuiltinCommand | interfaces/cli | 命令解析 | P0 |
| T13 | 实现 ConsolePresenter | interfaces/cli | 控制台输出格式化 | P0 |
| T14 | 实现 AgentCli 主循环 | interfaces/cli | CLI 交互循环 + 内置命令处理 | P0 |
| T15 | 实现 Main 入口 | 根包 | 依赖组装 + 启动 | P0 |
| T16 | 实现 DeepSeekConfig | infrastructure | 配置值对象 | P1 |
| T17 | 实现 DeepSeekHttpClient | infrastructure | HTTP 请求封装 + buildRequestBody | P1 |
| T18 | 实现 DeepSeekResponseParser | infrastructure | JSON → ChatResponse 解析 | P1 |
| T19 | 实现 DeepSeekChatModel | infrastructure | ChatModelPort 完整实现 | P1 |
| T20 | 实现 AppConfig | infrastructure | 环境变量配置聚合 | P1 |
| T21 | 编写单元测试 | 全部 | 覆盖率 ≥ 80% | P1 |
| T22 | 编写集成测试 | infrastructure | 真实 API 调用的验证 | P2 |
| T23 | Maven 打包验证 | 全部 | `mvn clean package` 生成 `target/flyagent.jar` | P2 |

---

## 12. 验收标准

| 编号 | 验收项 | 验证方式 |
| --- | --- | --- |
| AC1 | `mvn clean compile` 在 Java 17 下通过 | 命令行 |
| AC2 | `mvn test` 可执行且全部通过 | 命令行 |
| AC3 | `java -jar target/flyagent.jar` 可进入 CLI 交互循环 | 命令行 |
| AC4 | DDD 分层依赖方向清晰，domain 不依赖 interfaces/application/infrastructure | 代码审查 + ArchUnit 测试 |
| AC5 | `/help`、`/clear`、`/exit`、`/pwd`、`/session` 五个内置命令均可正常执行 | 手动测试 |
| AC6 | 输入自然语言文本后，Agent 调用 DeepSeek 并返回回答 | 手动测试 |
| AC7 | 每种 Message 类型序列化结果与 DeepSeek API 格式一致 | 单元测试 |
| AC8 | `ChatRequest.Builder` 缺少 messages 时抛出 `IllegalArgumentException` | 单元测试 |
| AC9 | 未设置 `DEEPSEEK_API_KEY` 时启动 CLI 后发送消息，显示清晰的错误提示 | 手动测试 |
| AC10 | HTTP 非 2xx 响应时抛出 `ApiException`，终端显示可读错误而不崩溃 | 单元测试 + 手动测试 |
| AC11 | content 为 JSON null（仅有 tool_calls）时正常解析，不抛 NPE | 单元测试 |
| AC12 | `mvn clean package` 通过，生成 `target/flyagent.jar` | 命令行 |

---

## 13. 风险与应对

| 风险 | 影响 | 应对 |
| --- | --- | --- |
| API Key 泄露到日志或代码 | 安全事件 | Key 只从环境变量读取，日志不打印 |
| Jackson 多态反序列化配置错误 | 消息路由失败 | 序列化/反序列化单元测试覆盖全部消息类型 |
| CLI `Scanner` 线程阻塞或编码问题 | 终端交互异常 | 使用标准 `Scanner(System.in)` + UTF-8 编码 |
| `System.in` 在部分终端（Git Bash）表现异常 | CLI 无法输入 | 提供 README 说明推荐终端，必要时引入 JLine |
| DeepSeek `thinking` 参数变更 | 请求失败 | 单一入口 `buildRequestBody` 封装 |
| 网络超时 | 用户等待后失败 | connect 30s / read 120s，超时后抛可读异常 |
| shade 插件 jar 冲突 | 打包失败 | 按依赖最小化原则，冲突时加 `<excludes>` |

---

## 14. 与其他模块的衔接

第一次开发构建的四层骨架是后续所有功能的基础：

```text
                    ChatModelPort
                         ↑
          ┌──────────────┼──────────────┐
          │              │              │
    第二次开发       第三次开发        第 N 次开发
  Tool 接口       AgentLoop      MySQL/Redis
  ToolRegistry    ReAct 循环      持久化 + 审批
  工具执行引擎    上下文管理      会话/任务落库
```

第二次开发将在本次基础上构建：
- `Tool` 接口与 `ToolRegistry`
- `list_files`、`read_file`、`search_text` 工具实现
- 工具执行上下文与结果模型

---

## 15. 参考资料

- DeepSeek API 文档：https://api-docs.deepseek.com/
- DeepSeek Chat Completions API：https://api-docs.deepseek.com/api/create-chat-completion
- OkHttp 官方文档：https://square.github.io/okhttp/
- Jackson 多态序列化：https://github.com/FasterXML/jackson-docs/wiki/JacksonPolymorphicDeserialization
- Maven Shade Plugin：https://maven.apache.org/plugins/maven-shade-plugin/
- FlyAgent 一期总体方案：`FlyAgent一期技术方案-Claude-Code风格CLI-Agent.md`
