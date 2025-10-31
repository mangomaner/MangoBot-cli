package org.mango;

import dev.langchain4j.community.model.dashscope.QwenChatModel;
import org.mango.manager.MessageCacheManager;
import org.mango.mangobot.annotation.QQ.method.DefaultMessage;
import org.mango.mangobot.annotation.QQ.method.ImageMessage;
import org.mango.mangobot.annotation.QQ.method.PokeMessage;
import org.mango.mangobot.annotation.QQ.method.TextMessage;
import org.mango.mangobot.model.dto.handler.ChatMessageDTO;
import org.mango.mangobot.plugin.Plugin;
import org.mango.mangobot.plugin.PluginContext;
import org.mango.model.ChatMessage;
import org.mango.service.ChatMessageService;
import org.springframework.beans.BeanUtils;
import redis.clients.jedis.JedisPool;

import java.util.List;
import java.util.logging.Logger;

public class ExamplePlugin implements Plugin {

    private static final Logger logger = Logger.getLogger(ExamplePlugin.class.getName());

    private QwenChatModel qwenChatModel;
    private JedisPool jedisPool; // 可配置更好;
    private ChatMessageService chatMessageService;
    private MessageCacheManager messageCacheManager = new MessageCacheManager(2); // 缓存最近10条消息

    public ExamplePlugin() {
        this.chatMessageService = new ChatMessageService(); // 初始化 Service
    }

    @Override
    public void onEnable(PluginContext context) {
        this.qwenChatModel = (QwenChatModel) context.getBean("qwenChatModel");
        logger.info("ExamplePlugin 已启用");
    }

    @Override
    public void onDisable() {
        logger.info("ExamplePlugin 已禁用");
        if (jedisPool != null) {
            jedisPool.close();
        }
    }

//    @ImageMessage
//    public void handleImageMessage(ChatMessageDTO chatMessageDTO){
//        ChatMessage chatMessage = new ChatMessage();
//        BeanUtils.copyProperties(chatMessageDTO, chatMessage);
//        logger.info("[ExamplePlugin] 收到图片消息");
//
//        chatMessageService.saveMessage(chatMessage);
//    }

    @PokeMessage
    public void handlePokeMessage(ChatMessageDTO chatMessageDTO){
        System.out.println("戳一戳");
    }

    @DefaultMessage
    public void handleCombineMessage(ChatMessageDTO chatMessageDTO){
        ChatMessage chatMessage = new ChatMessage();
        BeanUtils.copyProperties(chatMessageDTO, chatMessage);
        logger.info("[ExamplePlugin] 收到消息：" + chatMessage.getMessage()
                + " from: " + chatMessage.getUserId()
                + " target:" + chatMessage.getTargetId());

        boolean isDuplicate = messageCacheManager.isDuplicateMessage(chatMessage);
        if(isDuplicate)
            System.out.println("重复消息: " + chatMessageDTO.getMessage() + " " + chatMessage.getImageUrl());

        // 调用方法查找高频回复消息（比如 ≥ 2 次）
        List<ChatMessage> replies = chatMessageService.getFrequentReplies(chatMessage, 2);
        if (!replies.isEmpty()) {
            System.out.println("【高频回复消息】以下消息被多次回复：");
            for (ChatMessage msg : replies) {
                System.out.println(" - " + msg.getMessage() + " | " + msg.getFile());
            }
        }
        chatMessageService.saveMessage(chatMessage);
    }
    @TextMessage
    public void handleTextMessage(ChatMessageDTO chatMessageDTO){
    }
    @TextMessage
    @ImageMessage
    public void handleTextImageMessage(ChatMessage chatMessage){

    }
}