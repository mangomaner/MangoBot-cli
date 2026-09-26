package io.github.mangomaner.mangobot.module.agent.capability.tool;

import io.github.mangomaner.mangobot.module.agent.model.enums.SessionSource;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 一个对话轮次的上下文——发送工具靠它知道"往哪发、这轮已经发了几条"。
 *
 * <p><b>这里刻意没有"引用哪条"</b>。以前有个 {@code replyToMessageId}，由轮次编排无条件塞成本批的
 * 最后一条，于是 bot 的每一条消息都成了"回复某条"——而实测真人引用紧邻上一条只占 9.7%，
 * 绝大多数引用指的是已经被刷上去好几句的某条。引用是一个有指向的动作，只有模型知道它想指向谁，
 * 所以它成了发送工具的一个参数（{@code reply_to}），不再由这里代填。
 *
 * @param botId    发消息用的 bot（就是事件的 {@code self_id}）
 * @param source   会话来源（决定走群聊还是私聊接口）
 * @param targetId 目标群号 / 用户号
 * @param sends    本轮已发送条数，几个发送方法共用同一个计数器（上限是合计的）
 */
public record TurnContext(long botId, SessionSource source, long targetId, AtomicInteger sends) {

    public static TurnContext of(long botId, SessionSource source, long targetId) {
        return new TurnContext(botId, source, targetId, new AtomicInteger());
    }
}
