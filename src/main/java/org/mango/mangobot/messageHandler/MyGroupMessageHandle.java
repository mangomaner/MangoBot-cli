package org.mango.mangobot.messageHandler;

import cn.hutool.core.lang.UUID;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.community.model.dashscope.QwenChatRequestParameters;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.mango.mangobot.service.impl.GroupMessageService;
import org.mango.mangobot.utils.FileUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.*;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Component
@Slf4j
public class MyGroupMessageHandle implements GroupMessageHandler {
    @Resource
    QwenChatModel qwenChatModel;

    @PostConstruct
    public void init() {
    }

    @Override
    public void handleCombinationMessage(String fromUser, String content, String groupId, String imageUrl, String replyContent, String targetId) {
        System.out.println("处理消息：" + content + "from: " + fromUser);
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