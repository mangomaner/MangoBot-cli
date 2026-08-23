package io.github.mangomaner.mangobot.module.agent.chat;

import io.agentscope.core.message.ContentBlock;
import io.github.mangomaner.mangobot.adapter.onebot.handler.outbound.send.OneBotApiService;
import io.github.mangomaner.mangobot.adapter.onebot.model.event.message.OneBotMessageEvent;
import io.github.mangomaner.mangobot.adapter.onebot.model.segment.AtSegment;
import io.github.mangomaner.mangobot.adapter.onebot.model.segment.OneBotMessageSegment;
import io.github.mangomaner.mangobot.adapter.onebot.model.segment.ReplySegment;
import io.github.mangomaner.mangobot.adapter.onebot.model.vo.MessageInfo;
import io.github.mangomaner.mangobot.events.onebot.message.OneBotGroupMessageEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 群聊消息预处理：把一条原始群消息组装成带 message_id、图片、@/回复结构的信封行。
 *
 * <p>信封行格式（属性在前、内容在后）：
 * <pre>
 * [14:03] 张三(10001) message_id=7890 提到你:true
 *     今晚吃什么[图片]
 * </pre>
 * 第一行是属性：时间、发送者昵称(QQ)、message_id，仅在为 true 时追加 {@code 提到你}/{@code 回复你}，
 * 若有回复再追加以 {@code 回复了:<id>}；第二行缩进是正文。正文里的 {@code [@qq]} / {@code [图片]}
 * / {@code [表情]} 是模型字面标记，真实图片像素由 {@link MessageEnvelope} 以 ImageBlock 单独送达，
 * 绝不把图片描述化成文字进主模型；{@code [表情]}（QQ 表情/贴纸）只有标记、无像素。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GroupMessageContextBuilder {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final OneBotApiService oneBotApiService;
    private final MessageEnvelope messageEnvelope;

    /** 单条群消息的信封内容（元数据行 + 缩进正文）+ 图片块。 */
    public record Envelope(String line, List<ContentBlock> images) {
    }

    public Envelope build(OneBotGroupMessageEvent event) {
        String body = messageEnvelope.renderBody(event.getMessage());
        String replyTarget = messageEnvelope.replyTargetId(event.getMessage());
        boolean mention = detectMention(event, body);
        boolean reply = detectReplyToBot(event);

        StringBuilder line = new StringBuilder();
        line.append('[').append(TIME_FORMAT.format(Instant.ofEpochMilli(event.getTime() * 1000L)
                .atZone(ZoneId.systemDefault()))).append("] ")
                .append(formatSender(event))
                .append(" message_id=").append(event.getMessageId());
        if (replyTarget != null) {
            line.append(" 回复了:").append(replyTarget);
        }
        if (mention) {
            line.append(" 提到你:true");
        }
        if (reply) {
            line.append(" 回复你:true");
        }
        line.append("\n    ").append(body);

        return new Envelope(line.toString(), messageEnvelope.imageBlocks(event.getMessage()));
    }

    private String formatSender(OneBotGroupMessageEvent event) {
        OneBotMessageEvent.Sender sender = event.getSender();
        String name;
        if (sender != null) {
            name = sender.getCard();
            if (!StringUtils.hasText(name)) {
                name = sender.getNickname();
            }
            if (!StringUtils.hasText(name)) {
                name = String.valueOf(event.getUserId());
            }
        } else {
            name = String.valueOf(event.getUserId());
        }
        return name + "(" + event.getUserId() + ")";
    }

    /**
     * @我（不含 @全体）或正文中出现 bot 的 QQ 号（作为独立数字），即视为"提到你"。
     */
    private boolean detectMention(OneBotGroupMessageEvent event, String body) {
        String selfIdStr = String.valueOf(event.getSelfId());
        List<OneBotMessageSegment> segments = event.getMessage();
        if (segments != null) {
            for (OneBotMessageSegment segment : segments) {
                if (segment instanceof AtSegment at && at.getData() != null) {
                    String qq = at.getData().getQq();
                    if (qq != null && !"all".equals(qq) && selfIdStr.equals(qq)) {
                        return true;
                    }
                }
            }
        }
        return body != null && body.matches(".*(?<![0-9])" + selfIdStr + "(?![0-9]).*");
    }

    /**
     * 有人回复了 bot 发过的消息（回复消息的发送者是 bot 自己）。
     */
    private boolean detectReplyToBot(OneBotGroupMessageEvent event) {
        List<OneBotMessageSegment> segments = event.getMessage();
        if (segments == null) {
            return false;
        }
        for (OneBotMessageSegment segment : segments) {
            if (segment instanceof ReplySegment reply && reply.getData() != null) {
                try {
                    MessageInfo info = oneBotApiService.getMsg(
                            event.getSelfId(), Integer.parseInt(reply.getData().getId()));
                    if (info != null && info.getSender() instanceof java.util.Map<?, ?> map) {
                        Object userId = map.get("user_id");
                        if (userId != null && String.valueOf(userId).equals(String.valueOf(event.getSelfId()))) {
                            return true;
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to fetch reply message {} for group {}",
                            reply.getData().getId(), event.getGroupId(), e);
                }
            }
        }
        return false;
    }
}
