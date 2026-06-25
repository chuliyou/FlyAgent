---
name: project-architecture
description: FlyAgent 项目整体架构概览 —— DDD 六边形分层、核心领域模型、技术决策和扩展点
metadata:
  type: reference
---

# FlyAgent Architecture Overview

## Overall Architecture Style

Hexagonal (Ports & Adapters) + DDD layered architecture:

- **Interfaces (CLI)**: `AgentCli`, `CommandParser`, `ConsolePresenter` — READ-EVAL-PRINT loop
- **Application**: `AgentAppService`, `SessionAppService` — orchestration, returns DTOs
- **Domain**: Port interfaces (`ChatModelPort`), aggregate roots (`AgentSession`, `AgentTask`), value objects (`SessionId`, `TaskId`, `Workspace`)
- **Infrastructure**: Port implementations — `DeepSeekChatModel`, `DeepSeekHttpClient`, `DeepSeekResponseParser`, `AppConfig`
- **Common**: `FlyAgentException` hierarchy, `CommonConstants`

Dependency direction: interfaces → application → domain ← infrastructure
Domain layer has zero dependencies on outer layers.

## Tech Stack

- Java 17, Maven (shade plugin for fat jar)
- Jackson 2.18.2 (polymorphic Message deserialization via @JsonSubTypes)
- OkHttp 4.12.0 (sync HTTP client for DeepSeek API)
- SLF4J + Logback (logging)
- JUnit Jupiter + Mockito + AssertJ (testing)
- External: DeepSeek API (Chat Completions, function calling support)

## Core Domain Models

- **chat context**: `ChatModelPort` (interface), `ChatRequest` (Builder), `ChatResponse`, `ChatChoice`, `TokenUsage`
- **message context**: `Message` (polymorphic via `role` field), `SystemMessage`, `UserMessage`, `AssistantMessage` (factory methods `ofText`/`ofToolCalls`), `ToolMessage`
- **tool context**: `ToolDefinition`, `ToolParameter`, `JsonSchemaProperty`, `ToolCall`
- **session context**: `AgentSession` (aggregate root), `SessionId`, `SessionStatus`, `Workspace`
- **task context**: `AgentTask` (aggregate root, state machine: CREATED→RUNNING→COMPLETED/FAILED/HIT_MAX_TURNS), `TaskId`, `TaskStatus`

## Key Design Decisions

1. Pure DI (no Spring/Guice) — manual wiring in `Main.java` (acceptable for current simplicity)
2. Jackson polymorphic deserialization for Message system — zero-code routing by `role` field
3. Immutable domain objects + Builder pattern (`ChatRequest.Builder`)
4. Sync HTTP calls (OkHttp blocking) — suitable for CLI request-response pattern
5. In-memory session management (no persistence in M1+M3)
6. Default thinking mode enabled with reasoning_effort="high"

## Configuration Sources

- Priority: env vars → hardcoded defaults
- `DEEPSEEK_API_KEY` (required), `DEEPSEEK_BASE_URL` (default: api.deepseek.com), `DEEPSEEK_MODEL` (default: deepseek-v4-pro)
- Connect timeout: 30s, Read timeout: 120s

## Extension Points

1. `ChatModelPort` interface — swap model providers (DeepSeek → OpenAI → Anthropic) without touching domain/application
2. `Message` interface + `@JsonSubTypes` — add new message types with one annotation entry
3. `ToolDefinition` model — register new tools by constructing definition objects
4. `TaskStatus` enum — reserved states (WAITING_APPROVAL, REJECTED) for future approval workflows

## Current Limitations (M1+M3)

- Single-turn only (no message history)
- No ReAct loop implementation
- Tools defined but not implemented as executables
- No persistence layer
- No streaming support (though `stream` field exists in ChatRequest)
- No multi-environment configuration

## Build & Run

- `mvn package` → `target/flyagent.jar` (~6.5MB fat jar)
- Entry: `com.flyagent.Main`
- Optional CLI arg: workspace path (default: current directory)
