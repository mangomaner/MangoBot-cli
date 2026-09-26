package io.github.mangomaner.mangobot.message_handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mangomaner.mangobot.adapter.onebot.handler.inbound.process_file.OneBotMessageFileProcessor;
import io.github.mangomaner.mangobot.adapter.onebot.render.OneBotMessageParser;
import io.github.mangomaner.mangobot.annotation.PluginPriority;
import io.github.mangomaner.mangobot.annotation.messageHandler.MangoBotEventListener;
import io.github.mangomaner.mangobot.events.configuration.BotConfigChangedEvent;
import io.github.mangomaner.mangobot.events.onebot.message.OneBotGroupMessageEvent;
import io.github.mangomaner.mangobot.events.onebot.message.OneBotPrivateMessageEvent;
import io.github.mangomaner.mangobot.module.message.groupMessage.service.GroupMessagesService;
import io.github.mangomaner.mangobot.module.message.model.domain.GroupMessages;
import io.github.mangomaner.mangobot.module.message.model.domain.PrivateMessages;
import io.github.mangomaner.mangobot.module.message.privateMessage.service.PrivateMessagesService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@MangoBotEventListener
public class MessageStoreHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Resource
    private GroupMessagesService groupMessagesService;

    @Resource
    private PrivateMessagesService privateMessagesService;

    @Resource
    private OneBotMessageParser messageParser;

    @Resource
    private OneBotMessageFileProcessor oneBotMessageFileProcessor;

    @MangoBotEventListener
    @PluginPriority(-1)
    public boolean onGroupMessage(OneBotGroupMessageEvent event) {
        log.info("收到消息: " + event.getMessage());

        try {
            oneBotMessageFileProcessor.processReceivedFiles(event.getMessage());

            String parseMessage = messageParser.parseMessage(event.getMessage(), event.getSelfId());
            event.setParsedMessage(parseMessage);

            GroupMessages groupMessages = new GroupMessages();
            groupMessages.setBotId(String.valueOf(event.getSelfId()));
            groupMessages.setGroupId(String.valueOf(event.getGroupId()));
            groupMessages.setMessageId(String.valueOf(event.getMessageId()));
            groupMessages.setSenderId(String.valueOf(event.getUserId()));
            groupMessages.setMessageSegments(objectMapper.writeValueAsString(event.getMessage()));
            groupMessages.setMessageTime(event.getTime() * 1000L);
            groupMessages.setParseMessage(parseMessage);

            groupMessagesService.addGroupMessage(groupMessages);
        } catch (Exception e) {
            log.error("Failed to save group message", e);
        }
        return true;
    }

    @MangoBotEventListener
    @PluginPriority(-1)
    public boolean onPrivateMessage(OneBotPrivateMessageEvent event) {
        log.info("收到消息: " + event.getMessage());

        try {
            oneBotMessageFileProcessor.processReceivedFiles(event.getMessage());

            String parseMessage = messageParser.parseMessage(event.getMessage(), event.getSelfId());
            event.setParsedMessage(parseMessage);

            PrivateMessages privateMessages = new PrivateMessages();
            privateMessages.setBotId(String.valueOf(event.getSelfId()));
            privateMessages.setFriendId(String.valueOf(event.getUserId()));
            privateMessages.setMessageId(String.valueOf(event.getMessageId()));
            privateMessages.setSenderId(String.valueOf(event.getUserId()));
            privateMessages.setMessageSegments(objectMapper.writeValueAsString(event.getMessage()));
            privateMessages.setMessageTime(event.getTime() * 1000L);
            privateMessages.setParseMessage(parseMessage);
            privateMessagesService.save(privateMessages);
        } catch (Exception e) {
            log.error("Failed to save private message", e);
        }
        return true;
    }

    @MangoBotEventListener
    public boolean onConfigChange(BotConfigChangedEvent event) {
        log.info("收到 Bot 配置变更通知: key={}, value={}", event.getConfigKey(), event.getNewValue());
        return true;
    }

}
