package io.github.mangomaner.mangobot.module.agent.capability.tool;

import io.github.mangomaner.mangobot.module.agent.model.enums.SessionSource;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 一个对话轮次的上下文——发送工具靠它知道"往哪发、引用哪条、这轮已经发了几条"。
 *
 * @param botId           发消息用的 bot（就是事件的 {@code self_id}）
 * @param source          会话来源（决定走群聊还是私聊接口）
 * @param targetId        目标群号 / 用户号
 * @param replyToMessageId 要引用的消息 id，可为 null
 * @param sends           本轮已发送条数，两个发送方法共用同一个计数器（上限是合计的）
 */
public record TurnContext(long botId, SessionSource source, long targetId,
                          Long replyToMessageId, AtomicInteger sends) {

    public static TurnContext of(long botId, SessionSource source, long targetId, Long replyToMessageId) {
        return new TurnContext(botId, source, targetId, replyToMessageId, new AtomicInteger());
    }
}
