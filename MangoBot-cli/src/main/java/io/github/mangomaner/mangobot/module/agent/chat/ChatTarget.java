package io.github.mangomaner.mangobot.module.agent.chat;

import io.github.mangomaner.mangobot.module.agent.model.enums.SessionSource;

/**
 * 一个可对话的目标：哪个 bot、什么来源、发到哪。
 *
 * @param botId    收到消息的 bot（事件的 {@code self_id}）
 * @param source   群聊还是私聊
 * @param targetId 群号 / 用户号
 */
public record ChatTarget(long botId, SessionSource source, long targetId) {

    /**
     * 交给 {@code ClaudeRuntimeManager} 的"来源键"。
     *
     * <p>会话是按"同一个 bot 下的哪个聊天"分的，所以这里必须带上目标 id，不能只用来源枚举——
     * 否则同一个 bot 下所有群会共用一个 claude 进程、共享一份上下文。
     */
    public String runtimeSourceKey() {
        return source.getSourceKey() + ":" + targetId;
    }

    /**
     * 会话键，与 claude 会话键、MCP 请求头里的会话标识是同一个值。
     *
     * <p>形如 {@code 12345:group:67890}。tools 侧靠它从轮次注册表里找回上下文，
     * 所以这三处必须是同一个字符串——改了这里要同步改。
     */
    public String sessionKey() {
        return botId + ":" + runtimeSourceKey();
    }
}
