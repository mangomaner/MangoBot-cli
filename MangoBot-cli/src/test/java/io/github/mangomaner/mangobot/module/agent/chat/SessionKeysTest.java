package io.github.mangomaner.mangobot.module.agent.chat;

import io.github.mangomaner.mangobot.module.agent.model.enums.SessionSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 会话键的拼法——claude 会话、MCP 请求头、TurnSignal 三处必须是同一个字符串。 */
class SessionKeysTest {

    @Test
    @DisplayName("群聊/私聊的键是 botId:source:targetId")
    void qq() {
        assertEquals("3970521445:group:220264051",
                SessionKeys.qq(3970521445L, SessionSource.GROUP, 220264051L));
        assertEquals("3970521445:private:2756477287",
                SessionKeys.qq(3970521445L, SessionSource.PRIVATE, 2756477287L));
    }

    /**
     * web 的 sourceKey 就是会话 id，所以键只有两段。写成三段会去找一个不存在的目录，
     * 表现为"页面永远显示没有上下文"——静默失效，所以钉住。
     */
    @Test
    @DisplayName("web 的键是 web:会话id，不是 web:web:会话id")
    void web() {
        assertEquals("web:123", SessionKeys.web("123"));
        assertEquals("web:123", SessionKeys.of(SessionSource.WEB, "任意", "123"));
    }

    @Test
    @DisplayName("of() 按来源分流，群聊那条不会漏掉 botId")
    void ofBranchesBySource() {
        assertEquals("3970521445:group:220264051",
                SessionKeys.of(SessionSource.GROUP, "3970521445", "220264051"));
    }
}
