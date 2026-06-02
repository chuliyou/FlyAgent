package com.flyagent.domain.session;

import java.util.Objects;
import java.util.UUID;

/**
 * 会话 ID 值对象。
 *
 * <p>由前缀 "ses_" + UUID 前 8 位组成，全局唯一标识一次 CLI 会话。</p>
 *
 * @author FlyAgent Team
 * @since 1.0
 */
public class SessionId {

    private final String value;

    private SessionId(String value) {
        this.value = value;
    }

    /**
     * 生成一个新的会话 ID。
     *
     * @return 新的 SessionId
     */
    public static SessionId generate() {
        return new SessionId("ses_" + UUID.randomUUID().toString().substring(0, 8));
    }

    /**
     * 从已有值重建会话 ID。
     *
     * @param value ID 字符串值
     * @return SessionId
     * @throws IllegalArgumentException 当 value 为空时
     */
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
        if (this == o) {
            return true;
        }
        if (!(o instanceof SessionId)) {
            return false;
        }
        SessionId sessionId = (SessionId) o;
        return value.equals(sessionId.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
