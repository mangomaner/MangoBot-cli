package io.github.mangomaner.mangobot.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import io.github.mangomaner.mangobot.annotation.messageHandler.MangoBotEventListener;
import io.github.mangomaner.mangobot.manager.event.ConfigChangeEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@MangoBotEventListener
public class AiModelManager {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // 配置键常量
    public static final String MAIN_MODEL_KEY = "main.model.main_model";
    public static final String ASSISTANT_MODEL_KEY = "main.model.assistant_model";
    public static final String IMAGE_MODEL_KEY = "main.model.image_model";
    public static final String EmbeddingModelKey = "main.model.embedding_model";

    // JSON 字段常量
    private static final String BASE_URL_FIELD = "base_url";
    private static final String API_KEY_FIELD = "api-key";
    private static final String MODEL_NAME_FIELD = "model_name";
    private static final String EMBEDDING_MODEL_FIELD = "embedding_model";

    @MangoBotEventListener
    public boolean configChangeListener(ConfigChangeEvent event) {
        String key = event.getKey();

        // 只处理我们关心的三个模型配置
        if (!MAIN_MODEL_KEY.equals(key) &&
                !ASSISTANT_MODEL_KEY.equals(key) &&
                !IMAGE_MODEL_KEY.equals(key) &&
                !EmbeddingModelKey.equals(key)
        ) {
            return true; // 明确表示：这个事件我不处理
        }

        try {
            ModelConfig config = parseModelConfig(event.getValue());
            ChatLanguageModel model = createOpenAiChatModel(config);

            switch (key) {
                case MAIN_MODEL_KEY:
                    AiConfig.setMainModel(model);
                    log.debug("Main model 更新: {}", config.modelName);
                    break;
                case ASSISTANT_MODEL_KEY:
                    AiConfig.setAssistantModel(model);
                    log.debug("Assistant model 更新: {}", config.modelName);
                    break;
                case IMAGE_MODEL_KEY:
                    AiConfig.setImageModel(model);
                    log.debug("Image model 更新: {}", config.modelName);
                    break;
                case EmbeddingModelKey:
                    AiConfig.setEmbeddingModel(model);
                    log.debug("Embedding model 配置: {}", config.modelName);
                    break;
                default:
                    throw new IllegalStateException("Unexpected value: " + key);
            }
        } catch (Exception e) {
            log.error("Failed to update AI model for key: {}", key, e);
        }
        return true;
    }

    // 解析 JSON 配置为内部 DTO
    private ModelConfig parseModelConfig(String jsonValue) throws Exception {
        JsonNode node = objectMapper.readTree(jsonValue);
        String baseUrl = getRequiredText(node, BASE_URL_FIELD);
        String apiKey = getRequiredText(node, API_KEY_FIELD);
        String modelName = getRequiredText(node, MODEL_NAME_FIELD);
        return new ModelConfig(baseUrl, apiKey, modelName);
    }

    // 辅助方法：确保字段存在且非空
    private String getRequiredText(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        if (field == null || !field.isTextual() || field.asText().isBlank()) {
            throw new IllegalArgumentException("Missing or invalid field: " + fieldName);
        }
        return field.asText();
    }

    // 创建模型实例
    private ChatLanguageModel createOpenAiChatModel(ModelConfig config) {
        return OpenAiChatModel.builder()
                .baseUrl(config.baseUrl)
                .apiKey(config.apiKey)
                .modelName(config.modelName)
                .build();
    }

    // 内部 DTO，封装模型配置
    private static class ModelConfig {
        final String baseUrl;
        final String apiKey;
        final String modelName;

        ModelConfig(String baseUrl, String apiKey, String modelName) {
            this.baseUrl = baseUrl;
            this.apiKey = apiKey;
            this.modelName = modelName;
        }
    }
}
