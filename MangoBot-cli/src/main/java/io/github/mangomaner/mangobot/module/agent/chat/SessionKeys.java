package io.github.mangomaner.mangobot.module.agent.chat;

import io.github.mangomaner.mangobot.module.agent.model.enums.SessionSource;

/**
 * 会话键的<b>唯一算法</b>——claude 会话键、MCP 请求头里的会话标识、{@code TurnSignal} 的键
 * 必须是同一个字符串，所以要拼就在这一处拼。
 *
 * <p>两种形态：群聊/私聊是 {@code 12345:group:67890}；web 是 {@code web:<会话 id>}——
 * web 的"目标"本身就是会话 id，不再套一层来源段。
 */
public final class SessionKeys {

    /** web 会话挂的 bot 标识；它不是真的 bot id。 */
    public static final String WEB_BOT_ID = "web";

    private SessionKeys() {
    }

    // 群聊/私聊
    public static String qq(long botId, SessionSource source, long targetId) {
        return botId + ":" + source.getSourceKey() + ":" + targetId;
    }

    // web
    public static String web(String sessionId) {
        return WEB_BOT_ID + ":" + sessionId;
    }

    /** 从请求参数拼键——web 只看 {@code targetId}，其余要 botId + 来源 + 目标。 */
    public static String of(SessionSource source, String botId, String targetId) {
        return source == SessionSource.WEB
                ? web(targetId)
                : botId + ":" + source.getSourceKey() + ":" + targetId;
    }
}
