package io.github.mangomaner.mangobot.handler;

import io.github.mangomaner.mangobot.annotation.PluginPriority;
import io.github.mangomaner.mangobot.annotation.messageHandler.MangoBotEventListener;
import io.github.mangomaner.mangobot.manager.event.ConfigChangeEvent;
import io.github.mangomaner.mangobot.model.onebot.event.message.GroupMessageEvent;
import io.github.mangomaner.mangobot.model.onebot.event.message.PrivateMessageEvent;
import io.github.mangomaner.mangobot.service.GroupMessagesService;
import io.github.mangomaner.mangobot.service.PrivateMessagesService;
import io.github.mangomaner.mangobot.utils.MessageParser;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@MangoBotEventListener
public class MessageHandler {

    @Resource
    private GroupMessagesService groupMessagesService;

    @Resource
    private PrivateMessagesService privateMessagesService;

    @Resource
    private MessageParser messageParser;

    @MangoBotEventListener
    @PluginPriority(-1)
    public boolean onGroupMessage(GroupMessageEvent event) {
        log.info("收到消息: " + event.getMessage());
        String parseMessage = messageParser.parseMessage(event.getMessage(), event.getSelfId());
        event.setParsedMessage(parseMessage);
        groupMessagesService.addGroupMessage(event);
        return true;
    }

    @MangoBotEventListener
    @PluginPriority(-1)
    public boolean onPrivateMessage(PrivateMessageEvent event) {
        log.info("收到消息: " + event.getMessage());
        String parseMessage = messageParser.parseMessage(event.getMessage(), event.getSelfId());
        event.setParsedMessage(parseMessage);
        privateMessagesService.addPrivateMessage(event);
        return true;
    }

    @MangoBotEventListener
    public boolean onConfigChange(ConfigChangeEvent event) {    
        log.info("收到配置变更通知: key={}, value={}", event.getKey(), event.getValue());
        return true;
    }

}
