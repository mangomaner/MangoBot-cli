package org.mango.mangobot.messageHandler;

import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.community.model.dashscope.QwenChatRequestParameters;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.mango.mangobot.annotation.QQ.method.TextMessage;
import org.mango.mangobot.plugin.MessageDispatcher;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class MyGroupMessageHandle implements GroupMessageHandler {
    @Resource
    QwenChatModel qwenChatModel;

    @Resource
    private MessageDispatcher messageDispatcher;

    @PostConstruct
    public void init() {
    }

    @Override
    public void handleCombinationMessage(String fromUser, String content, String groupId, String imageUrl, String replyContent, String targetId) {
        System.out.println("处理消息：" + content + "from: " + fromUser);
    }

    @Override
    public void handleTextMessage(String fromUser, String content) {
        System.out.println("主处理器 - 收到文本消息：" + content);
        messageDispatcher.dispatchMessage(TextMessage.class, fromUser, content);
    }

    @Override
    public void handleTextWithAtMessage(String fromUser, String content, String targetId) {
        System.out.println("处理文本+At消息：" + content + "from: " + fromUser);
    }

    @Override
    public void handlePoke(String fromUser, String targetUser, String groupId) {
        System.out.println("戳一戳：" + fromUser + " to " + targetUser);
    }

    private String chatWithModel(String question) {
        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from(question))
                .parameters(QwenChatRequestParameters.builder()
                        .temperature(0.5)
                        .modelName("qwen-turbo") // 设置模型名称
                        .enableSearch(false)    // 是否联网搜索
                        .build())
                .build();
        ChatResponse chatResponse = qwenChatModel.chat(request);
        return chatResponse.aiMessage().text();
    }
}