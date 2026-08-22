package io.github.mangomaner.mangobot.message_handler.response;

import io.github.mangomaner.mangobot.annotation.PluginPriority;
import io.github.mangomaner.mangobot.annotation.messageHandler.MangoBotEventListener;
import io.github.mangomaner.mangobot.api.MangoAgentApi;
import io.github.mangomaner.mangobot.events.onebot.message.OneBotGroupMessageEvent;
import io.github.mangomaner.mangobot.events.onebot.message.OneBotPrivateMessageEvent;
import io.github.mangomaner.mangobot.module.agent.chat.GroupMessageDebounce;
import io.github.mangomaner.mangobot.module.agent.model.enums.SessionSource;
import io.github.mangomaner.mangobot.module.agent.model.vo.ChatSessionVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 群聊 / 私聊 AI 对话入口
 *
 * <p>v2 升级后职责极简：
 * <ul>
 *   <li>群聊消息先进 {@link GroupMessageDebounce}：预处理成带发送者/@/回复/时间间隔的信封，
 *       再经 1 秒 leading-edge 合并（按消息时间排序）后一次性交给 Agent；</li>
 *   <li>私聊消息直接交给 {@link MangoAgentApi#streamChat(Integer, String, String)}，
 *       发送者身份通过 UserMessage.name 带给模型，会话历史由 AgentState 承载；</li>
 *   <li>同一会话有 in-flight 调用时，ChatOrchestrator 自动打断旧调用并基于新消息重新决策
 *       （运行中打断 + 合并续答）；</li>
 *   <li>私聊必须回复：ChatOrchestrator 检测到未调用发送工具时会自动重试一次。</li>
 * </ul>
 */
@Component
@Slf4j
@RequiredArgsConstructor
@MangoBotEventListener
public class MessageResponseHandler {

    private final GroupMessageDebounce groupMessageDebounce;

    @MangoBotEventListener
    @PluginPriority(1000)
    public boolean onGroupMessage(OneBotGroupMessageEvent event) {
        String text = extractText(event.getParsedMessage(), event.getRawMessage());
        if (text == null) {
            return false;
        }
        // 群聊多人群：先预处理成带发送者/@/回复/时间间隔的信封，再进 1 秒合并缓冲，到点按消息时间排序后一次性触发 bot
        groupMessageDebounce.enqueue(event, text);
        return false;
    }

    @MangoBotEventListener
    @PluginPriority(1000)
    public boolean onPrivateMessage(OneBotPrivateMessageEvent event) {
        String text = extractText(event.getParsedMessage(), event.getRawMessage());
        if (text == null) {
            return false;
        }
        ChatSessionVO session = MangoAgentApi.getSessionByBotIdAndChatId(
                String.valueOf(event.getSelfId()),
                String.valueOf(event.getUserId()),
                SessionSource.PRIVATE);
        MangoAgentApi.streamChat(session.getId(), text, String.valueOf(event.getUserId())).subscribe();
        return false;
    }

    private String extractText(String parsedMessage, String rawMessage) {
        if (StringUtils.hasText(parsedMessage)) {
            return parsedMessage;
        }
        if (StringUtils.hasText(rawMessage)) {
            return rawMessage;
        }
        return null;
    }
}
