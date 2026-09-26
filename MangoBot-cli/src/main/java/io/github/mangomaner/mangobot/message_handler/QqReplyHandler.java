package io.github.mangomaner.mangobot.message_handler;

import io.github.mangomaner.mangobot.adapter.onebot.model.event.message.OneBotMessageEvent;
import io.github.mangomaner.mangobot.adapter.onebot.model.segment.ImageSegment;
import io.github.mangomaner.mangobot.adapter.onebot.model.segment.OneBotMessageSegment;
import io.github.mangomaner.mangobot.adapter.onebot.model.segment.ReplySegment;
import io.github.mangomaner.mangobot.adapter.onebot.utils.ChatEnvelope;
import io.github.mangomaner.mangobot.adapter.onebot.utils.MessageLexer;
import io.github.mangomaner.mangobot.annotation.PluginPriority;
import io.github.mangomaner.mangobot.annotation.messageHandler.MangoBotEventListener;
import io.github.mangomaner.mangobot.api.MangoFileApi;
import io.github.mangomaner.mangobot.events.onebot.message.OneBotGroupMessageEvent;
import io.github.mangomaner.mangobot.events.onebot.message.OneBotPrivateMessageEvent;
import io.github.mangomaner.mangobot.module.agent.chat.ChatInboxRegistry;
import io.github.mangomaner.mangobot.module.agent.chat.ChatTarget;
import io.github.mangomaner.mangobot.module.agent.chat.IncomingLine;
import io.github.mangomaner.mangobot.module.agent.model.enums.SessionSource;
import io.github.mangomaner.mangobot.module.file.model.domain.BotFiles;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * QQ 入站消息 → 回复链路的第一站：把消息信封化后投进收件箱，然后就返回。
 *
 * <p><b>这个类在派发线程上跑</b>（{@code MangoEventPublisher} 那个单线程池），所以它只做五件事：
 * self 判断、messageId 去重、为 image 段查一次 {@code bot_files}、渲染信封、投递。
 * 发送、跑 claude、读图片字节统统不在这一层——那些都在 {@code ChatInboxRegistry} 的池子上。
 *
 * <p>优先 {@link PluginPriority} 取 0，晚于 {@code MessageStoreHandler} 的 {@code -1}：
 * 那时候 {@code OneBotMessageFileProcessor} 已经下载完图并写好了 {@code bot_files} 行，
 * 这里查得到图片描述与本地路径。
 *
 * <p>黑白名单不用管：{@code MangoEventPublisher} 在派发之前就拦掉了，被拦的事件这里根本收不到。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@MangoBotEventListener
public class QqReplyHandler {

    /** messageId 去重窗口；只防"同一条被派发两次"，够用即可。 */
    private static final int SEEN_CACHE_SIZE = 512;

    private final ChatInboxRegistry inboxRegistry;

    /** 最近见过的 messageId（LRU）；聊天里 messageId 是 int。 */
    private final Map<Integer, Boolean> seenMessageIds = new LinkedHashMap<>(SEEN_CACHE_SIZE, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, Boolean> eldest) {
            return size() > SEEN_CACHE_SIZE;
        }
    };

    @MangoBotEventListener
    @PluginPriority(0)
    public boolean onGroupMessage(OneBotGroupMessageEvent event) {
        handle(event, SessionSource.GROUP, event.getGroupId());
        return true;
    }

    @MangoBotEventListener
    @PluginPriority(0)
    public boolean onPrivateMessage(OneBotPrivateMessageEvent event) {
        handle(event, SessionSource.PRIVATE, event.getUserId());
        return true;
    }

    // ========================================================================
    // 内部
    // ========================================================================

    private void handle(OneBotMessageEvent event, SessionSource source, long targetId) {
        // bot 自己发的消息也会作为事件回来（MessageStoreHandler 无差别入库），不挡掉就是自己回自己
        if (event.getUserId() == event.getSelfId()) {
            return;
        }
        if (!markSeen(event.getMessageId())) {
            return;
        }

        try {
            Map<String, BotFiles> images = loadImages(event.getMessage());
            String body = MessageLexer.render(event.getMessage(), MessageLexer.RenderMode.ENVELOPE,
                    fileId -> {
                        BotFiles file = images.get(fileId);
                        return file == null ? null : file.getDescription();
                    });

            ChatTarget target = new ChatTarget(event.getSelfId(), source, targetId);
            IncomingLine line = new IncomingLine(
                    event.getMessageId(),
                    extractReplyToMessageId(event.getMessage()),
                    envelope(event, body),
                    new ArrayList<>(images.values()));
            inboxRegistry.submit(target, line);
        } catch (Exception e) {
            log.error("入站消息投递失败：source={} target={}", source.getSourceKey(), targetId, e);
        }
    }

    /** {@code [2026-09-25 14:03:11] 张三(123456): 你好啊}；发送者名优先群名片。 */
    private static String envelope(OneBotMessageEvent event, String body) {
        String name = null;
        if (event.getSender() != null) {
            name = event.getSender().getCard();
            if (name == null || name.isBlank()) {
                name = event.getSender().getNickname();
            }
        }
        if (name == null || name.isBlank()) {
            name = String.valueOf(event.getUserId());
        }
        String time = ChatEnvelope.formatTime(event.getTime());
        return ChatEnvelope.render(time, name, event.getUserId(), body, (long) event.getMessageId());
    }

    /**
     * 只为 image 段查一次 {@code bot_files}，结果按段顺序去重。
     *
     * <p>这一份同时供两处用：信封里的 {@code [图片#<fileId>: 描述]}，以及轮次里读字节送像素。
     * 查库本身与 {@code MessageStoreHandler} 在同一线程做的是同一类事，不引入新的风险类别。
     */
    private Map<String, BotFiles> loadImages(List<OneBotMessageSegment> segments) {
        Map<String, BotFiles> images = new LinkedHashMap<>();
        if (segments == null) {
            return images;
        }
        for (OneBotMessageSegment segment : segments) {
            if (segment == null || !"image".equals(segment.getType())) {
                continue;
            }
            ImageSegment.ImageData data = ((ImageSegment) segment).getData();
            String fileId = data == null ? null : data.getFile();
            if (fileId == null || fileId.isEmpty() || images.containsKey(fileId)) {
                continue;
            }
            BotFiles file = MangoFileApi.getFileByFileId(fileId);
            if (file == null) {
                log.warn("图片段在 bot_files 里查不到，信封里只会有 [图片]：fileId={}", fileId);
                continue;
            }
            images.put(fileId, file);
        }
        return images;
    }

    /** 取 reply 段的引用目标；没有或不是数字都返回 null。 */
    private static Long extractReplyToMessageId(List<OneBotMessageSegment> segments) {
        if (segments == null) {
            return null;
        }
        for (OneBotMessageSegment segment : segments) {
            if (segment == null || !"reply".equals(segment.getType())) {
                continue;
            }
            ReplySegment.ReplyData data = ((ReplySegment) segment).getData();
            String id = data == null ? null : data.getId();
            if (id == null || id.isBlank()) {
                continue;
            }
            try {
                return Long.parseLong(id.trim());
            } catch (NumberFormatException e) {
                log.warn("reply 段的 id 不是数字，忽略：{}", id);
                return null;
            }
        }
        return null;
    }

    private synchronized boolean markSeen(int messageId) {
        return seenMessageIds.put(messageId, Boolean.TRUE) == null;
    }
}
