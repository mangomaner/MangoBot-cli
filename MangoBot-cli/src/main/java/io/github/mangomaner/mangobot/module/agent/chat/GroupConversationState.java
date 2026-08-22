package io.github.mangomaner.mangobot.module.agent.chat;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 群聊会话状态：记录每个群的最后一条消息时间，供 GroupMessageContextBuilder 计算"距上条"。
 */
@Component
public class GroupConversationState {

    /** groupKey(botId:groupId) -> 该群最后一条消息时间(epoch millis) */
    private final ConcurrentHashMap<String, Long> lastMessageTimes = new ConcurrentHashMap<>();

    /**
     * 记录当前消息时间，返回该群上一条消息的时间（无记录返回 null）。
     *
     * @return 上一次记录的时间戳，用于计算两条消息的间隔
     */
    public Long recordMessage(String groupKey, long time) {
        return lastMessageTimes.put(groupKey, time);
    }
}
