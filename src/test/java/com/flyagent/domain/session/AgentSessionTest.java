package com.flyagent.domain.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AgentSession 单元测试。
 *
 * @author FlyAgent Team
 * @since 1.0
 */
class AgentSessionTest {

    @Test
    @DisplayName("创建会话后状态应为 ACTIVE")
    void shouldBeActiveAfterCreation() {
        Workspace workspace = new Workspace(".");
        AgentSession session = new AgentSession(workspace);

        assertThat(session.isActive()).isTrue();
        assertThat(session.getStatus()).isEqualTo(SessionStatus.ACTIVE);
        assertThat(session.getId()).isNotNull();
        assertThat(session.getId().getValue()).startsWith("ses_");
    }

    @Test
    @DisplayName("关闭会话后状态应为 CLOSED")
    void shouldBeClosedAfterClose() {
        Workspace workspace = new Workspace(".");
        AgentSession session = new AgentSession(workspace);

        session.close();

        assertThat(session.isActive()).isFalse();
        assertThat(session.getStatus()).isEqualTo(SessionStatus.CLOSED);
        assertThat(session.getClosedAt()).isNotNull();
    }

    @Test
    @DisplayName("已关闭的会话调用 activate() 应抛出异常")
    void shouldThrowWhenActivatingClosedSession() {
        Workspace workspace = new Workspace(".");
        AgentSession session = new AgentSession(workspace);
        session.close();

        assertThatThrownBy(session::activate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot activate a closed session");
    }

    @Test
    @DisplayName("Workspace 路径为空时应抛出异常")
    void shouldThrowWhenWorkspaceIsBlank() {
        assertThatThrownBy(() -> new Workspace(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be blank");
    }

    @Test
    @DisplayName("SessionId.generate 应生成以 ses_ 开头的 ID")
    void shouldGenerateUniqueSessionId() {
        SessionId id1 = SessionId.generate();
        SessionId id2 = SessionId.generate();

        assertThat(id1.getValue()).startsWith("ses_");
        assertThat(id1).isNotEqualTo(id2);
    }

    @Test
    @DisplayName("同一值的 SessionId 应相等")
    void shouldEqualSameSessionId() {
        SessionId id1 = SessionId.of("ses_test");
        SessionId id2 = SessionId.of("ses_test");

        assertThat(id1).isEqualTo(id2);
        assertThat(id1.hashCode()).isEqualTo(id2.hashCode());
    }
}
