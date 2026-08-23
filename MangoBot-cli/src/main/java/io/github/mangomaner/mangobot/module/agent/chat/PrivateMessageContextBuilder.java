package io.github.mangomaner.mangobot.module.agent.chat;

import io.agentscope.core.message.ContentBlock;
import io.github.mangomaner.mangobot.adapter.onebot.model.event.message.OneBotMessageEvent;
import io.github.mangomaner.mangobot.events.onebot.message.OneBotPrivateMessageEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 私聊消息信封：把一条原始私聊消息组装成带 message_id、图片的信封内容块。
 *
 * <pre>
 * [14:03] 用户(10002) message_id=456
 *     看看这个[图片]
 * </pre>
 */
@Component
@RequiredArgsConstructor
public class PrivateMessageContextBuilder {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final MessageEnvelope messageEnvelope;

    public List<ContentBlock> build(OneBotPrivateMessageEvent event) {
        String body = messageEnvelope.renderBody(event.getMessage());
        String replyTarget = messageEnvelope.replyTargetId(event.getMessage());

        String line = '[' + TIME_FORMAT.format(Instant.ofEpochMilli(event.getTime() * 1000L)
                .atZone(ZoneId.systemDefault()))
                + "] " + formatSender(event)
                + " message_id=" + event.getMessageId()
                + (replyTarget != null ? " 回复了:" + replyTarget : "")
                + "\n    " + body + "\n";

        List<ContentBlock> blocks = new ArrayList<>();
        blocks.add(messageEnvelope.text(line));
        blocks.addAll(messageEnvelope.imageBlocks(event.getMessage()));
        return blocks;
    }

    private String formatSender(OneBotPrivateMessageEvent event) {
        OneBotMessageEvent.Sender sender = event.getSender();
        if (sender != null) {
            String name = sender.getCard();
            if (!StringUtils.hasText(name)) {
                name = sender.getNickname();
            }
            if (StringUtils.hasText(name)) {
                return name + "(" + event.getUserId() + ")";
            }
        }
        return "用户(" + event.getUserId() + ")";
    }
}
