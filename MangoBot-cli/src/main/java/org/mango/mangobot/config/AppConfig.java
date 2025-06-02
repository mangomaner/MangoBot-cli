package org.mango.mangobot.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.community.model.dashscope.QwenChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Configuration
public class AppConfig {

    @Value("${chat-model.api-key}")
    private String apiKey;

    @Value("${chat-model.model-name}")
    private String modelName;

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean QwenChatModel qwenChatModel(){
        return QwenChatModel.builder()
                .apiKey(apiKey)
                .modelName("qwen-turbo")
                .listeners(List.of(new MyChatModelListener()))
                .build();
    }

    @Bean ObjectMapper objectMapper() {
        return new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

}
