package org.mango.mangobot.controller;

import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.community.model.dashscope.QwenChatRequestParameters;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    @Resource
    public QwenChatModel qwenChatModel;

    @GetMapping("/workFlowTest")
    public String chat(@RequestParam String question) {
        return chatWithModel(question);
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
