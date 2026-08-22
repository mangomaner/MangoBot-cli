package io.github.mangomaner.mangobot.module.agent.chat;

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

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 群聊消息预处理：把一条原始群消息组装成带上下文的信封，供 Agent 理解。
 *
 * <p>信封格式：
 * <pre>
 * [14:03] 张三(10001·群员) → 今晚吃什么
 * 提到你:true 回复你:false 距上条:5分钟
 * </pre>
 * <ul>
 *   <li>提到你：有人 @你（不含 @全体）或在正文里写了你的 QQ 号</li>
 *   <li>回复你：有人回复了你发过的消息</li>
 *   <li>距上条：距离上一条群消息的时间，仅供 Agent 参考，不作为话题切换依据</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GroupMessageContextBuilder {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final GroupConversationState conversationState;
    private final OneBotApiService oneBotApiService;

    /**
     * 构建群消息信封。
     *
     * @param event 群消息事件
     * @param body  消息正文（parse_message）
     */
    public String build(OneBotGroupMessageEvent event, String body) {
        String groupKey = event.getSelfId() + ":" + event.getGroupId();
        long now = event.getTime() * 1000L;
        Long last = conversationState.recordMessage(groupKey, now);

        StringBuilder envelope = new StringBuilder();
        envelope.append('[')
                .append(TIME_FORMAT.format(Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault())))
                .append("] ")
                .append(formatSender(event))
                .append(" → ")
                .append(body == null ? "" : body)
                .append('\n')
                .append("提到你:").append(detectMention(event, body))
                .append(" 回复你:").append(detectReplyToBot(event))
                .append(" 距上条:").append(formatGap(last, now));
        return envelope.toString();
    }

    private String formatSender(OneBotGroupMessageEvent event) {
        OneBotMessageEvent.Sender sender = event.getSender();
        String name;
        String roleSuffix = "";
        if (sender != null) {
            name = sender.getCard();
            if (name == null || name.isEmpty()) {
                name = sender.getNickname();
            }
            if (name == null || name.isEmpty()) {
                name = String.valueOf(event.getUserId());
            }
            if (sender.getRole() != null && !sender.getRole().isEmpty()) {
                roleSuffix = "·" + sender.getRole();
            }
        } else {
            name = String.valueOf(event.getUserId());
        }
        return name + "(" + event.getUserId() + roleSuffix + ")";
    }

    /**
     * @我（不含 @全体）或正文中出现 bot 的 QQ 号（作为独立数字），即视为"提到你"。
     */
    private boolean detectMention(OneBotGroupMessageEvent event, String body) {
        long selfId = event.getSelfId();
        String selfIdStr = String.valueOf(selfId);
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
                    if (info != null && info.getSender() instanceof Map<?, ?> map) {
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

    private String formatGap(Long last, long now) {
        if (last == null) {
            return "新会话";
        }
        long gapMinutes = (now - last) / 60000;
        if (gapMinutes < 1) {
            return "刚刚";
        }
        if (gapMinutes < 60) {
            return gapMinutes + "分钟";
        }
        long hours = gapMinutes / 60;
        if (hours < 24) {
            return hours + "小时";
        }
        return (hours / 24) + "天";
    }
}
