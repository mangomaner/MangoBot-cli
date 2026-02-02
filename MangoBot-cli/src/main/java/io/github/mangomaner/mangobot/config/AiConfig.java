package io.github.mangomaner.mangobot.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.Getter;

/**
 * 纯静态配置类，用于向插件或外部调用者提供 AI 模型实例。
 * 模型的更新由 AiModelManager (Spring Bean) 负责。
 */
public class AiConfig {

    private AiConfig() {
        // 私有构造函数，防止实例化
    }

    @Getter
    private static volatile ChatLanguageModel mainModel = null;
    @Getter
    private static volatile ChatLanguageModel assistantModel = null;
    @Getter
    private static volatile ChatLanguageModel imageModel = null;
    @Getter
    private static volatile ChatLanguageModel embeddingModel = null;

    // 包级私有 Setter，仅供同包下的 Manager 调用
    static void setMainModel(ChatLanguageModel model) {
        mainModel = model;
    }

    static void setAssistantModel(ChatLanguageModel model) {
        assistantModel = model;
    }

    static void setImageModel(ChatLanguageModel model) {
        imageModel = model;
    }

    static void setEmbeddingModel(ChatLanguageModel model) {
        embeddingModel = model;
    }
}
