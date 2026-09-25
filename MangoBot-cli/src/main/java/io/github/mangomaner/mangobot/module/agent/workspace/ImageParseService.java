package io.github.mangomaner.mangobot.module.agent.workspace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.mangomaner.mangobot.api.MangoModelApi;
import io.github.mangomaner.mangobot.api.enums.ModelRole;
import io.github.mangomaner.mangobot.module.configuration.model.domain.ModelEndpoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 图片解析服务
 * <p>
 * 直接用 IMAGE 角色的模型配置发一次 OpenAI 兼容的 chat/completions 请求，
 * 支持表情包分析和通用图片分析。Token 用量和耗时信息会直接输出到日志。
 */
@Slf4j
@Service
public class ImageParseService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 单次图片解析的 HTTP 超时（视觉模型响应较慢，给足时间） */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(120);

    /**
     * 表情包分析提示词
     */
    private static final String MEME_PARSE_PROMPT = """
            # Role
            你是一位擅长捕捉视觉情绪的表情包分析师。

            # Task
            请分析上传图片，将**关键的视觉特征**与**核心情感**直接挂钩，解释"画面表现了什么"以及"这代表了什么情绪"。

            # Output Format
            请仅输出**一段连贯的文字**（约 20-50 字），结构如下：
            先完整输出屏幕中展示的文字（不存在则跳过该部分），然后简要描述图中**最传神的视觉细节**（如眼神、表情、肢体动作），紧接着用精准的形容词定义其表达的**核心情感基调**（需包含情绪的细微层次）。
            """;

    /**
     * 通用图片分析提示词
     */
    private static final String IMAGE_PARSE_PROMPT = """
            # Role
            你是一位严谨的"视觉事实记录员"。你的任务是客观、准确地还原图片中的可见内容，不进行任何主观推测、情感渲染或故事化叙述。

            # Guidelines
            1. **绝对客观**：只描述画面中实际存在的物体、人物动作、场景布局和数据事实。禁止使用"看起来"、"似乎"、"可能"、"感觉"等推测性词汇。
            2. **去位置化**：提取文字时，只需列出内容本身，无需说明文字在图中的具体位置（如"左上角"、"底部"）。
            3. **结构化优先**：输出应条理清晰，直接呈现事实清单和文字内容。
            4. **完整性**：尽可能覆盖图中所有可见的关键元素和文本信息。

            # Task
            请对上传的图片执行以下两步操作：

            ## 1. 客观内容陈述 (Objective Description)
            - 以清单或简练段落的形式，罗列图中的核心视觉元素。
            - **如果是场景/人物**：描述主体是谁、穿着什么、正在做什么动作、周围有什么具体物体。
            - **如果是图表/数据**：直接陈述图表类型、坐标轴含义、关键数据点及数值对比。
            - **如果是文档/界面**：描述文档的结构板块、界面的主要功能区域。
            - *注意：严禁添加背景故事、情绪分析或对未发生事情的预测。*

            ## 2. 纯文本提取 (Pure Text Extraction)
            - 按阅读逻辑提取图中所有可见文字。
            - 保持原文的层级结构（如：标题、副标题、正文、列表项）。
            - 仅输出文字内容，不要标注位置或解释其作用。
            - 若遇到模糊不清的字，用 `[?]` 标记。

            # Output Format
            请严格遵循以下格式输出：

            ---
            ### 📷 客观视觉事实
            - [事实点 1：例如：画面中央有一名身穿红色制服的男子，手持麦克风正在讲话。]
            - [事实点 2：例如：背景是一个带有蓝色横幅的会议室，横幅上印有白色标志。]
            - [事实点 3：例如：左侧桌面上放置着一台打开的笔记本电脑和一个水杯。]
            *(若是图表/数据图，请直接陈述数据事实，如：2023 年销售额为 500 万，同比增长 20%。)*

            ### 📄 图中文字内容
            **[标题/主标语]**
            [提取的标题文字]

            **[正文/详细信息]**
            [提取的正文段落或列表内容]
            [提取的其他标签、按钮文字或数据标注]

            *(注：若文字较少，可直接合并列出；若较多，请按视觉层级分段；若很多，如超过150字，请在保留原义的前提下进行压缩)*
            ---
            """;

    /**
     * 解析表情包图片
     *
     * @param url 图片 URL
     * @return 解析后的内容，失败返回 null
     */
    public String parseMeme(String url) {
        return parseImage(MEME_PARSE_PROMPT, url, "meme");
    }

    /**
     * 解析通用图片
     *
     * @param url 图片 URL
     * @return 解析后的内容，失败返回 null
     */
    public String parseImage(String url) {
        return parseImage(IMAGE_PARSE_PROMPT, url, "image");
    }

    /**
     * 解析图片内容
     *
     * @param systemPrompt 系统提示词
     * @param url          图片 URL
     * @param imageType    图片类型标识（用于日志）
     * @return 解析后的内容，失败返回 null
     */
    private String parseImage(String systemPrompt, String url, String imageType) {
        try {
            ModelEndpoint endpoint = MangoModelApi.resolveEndpoint(ModelRole.IMAGE);
            if (!endpoint.ok()) {
                log.warn("Image model not available, skipping image parsing for url: {} ({})",
                        url, endpoint.problem());
                return null;
            }

            String requestBody = buildRequestBody(endpoint, systemPrompt, url);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(chatCompletionsUrl(endpoint.baseUrl())))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + endpoint.apiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();

            long startTime = System.currentTimeMillis();
            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            long duration = System.currentTimeMillis() - startTime;

            if (response.statusCode() != 200) {
                log.warn("Image parsing HTTP {}: url={}, body={}",
                        response.statusCode(), url, truncate(response.body()));
                return null;
            }

            JsonNode root = OBJECT_MAPPER.readTree(response.body());
            JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
            if (contentNode.isMissingNode() || contentNode.isNull()) {
                log.warn("Empty response from image parsing: url={}", url);
                return null;
            }

            String content = contentNode.asText();
            if (content == null || content.isBlank()) {
                log.warn("Empty response from image parsing: url={}", url);
                return null;
            }

            long inputTokens = root.path("usage").path("prompt_tokens").asLong(-1);
            long outputTokens = root.path("usage").path("completion_tokens").asLong(-1);
            if (inputTokens >= 0 || outputTokens >= 0) {
                log.info("[ImageParse] 类型: {}, 输入: {} tokens, 输出: {} tokens, 耗时: {}ms",
                        imageType, inputTokens, outputTokens, duration);
            } else {
                log.info("[ImageParse] 类型: {}, 耗时: {}ms (无 Token 用量统计)", imageType, duration);
            }

            return "<image>" + content + "</image>";
        } catch (Exception e) {
            log.error("Failed to parse image: url={}, type={}", url, imageType, e);
            return null;
        }
    }

    /**
     * 组装 OpenAI 兼容的 chat/completions 请求体，生成参数（temperature / max_tokens / top_p）
     * 取该角色模型的配置值，未配置则不下发（交给服务端默认值）。
     */
    private String buildRequestBody(ModelEndpoint endpoint, String systemPrompt, String imageUrl) {
        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        root.put("model", endpoint.modelName());

        ArrayNode messages = root.putArray("messages");
        messages.addObject().put("role", "system").put("content", systemPrompt);

        ObjectNode userMessage = messages.addObject();
        userMessage.put("role", "user");
        ArrayNode userContent = userMessage.putArray("content");
        userContent.addObject().put("type", "text").put("text", "请你进行分析");
        userContent.addObject()
                .put("type", "image_url")
                .putObject("image_url")
                .put("url", imageUrl);

        if (endpoint.temperature() != null) {
            root.put("temperature", endpoint.temperature());
        }
        if (endpoint.maxTokens() != null) {
            root.put("max_tokens", endpoint.maxTokens());
        }
        if (endpoint.topP() != null) {
            root.put("top_p", endpoint.topP());
        }

        return root.toString();
    }

    private static String chatCompletionsUrl(String baseUrl) {
        String trimmed = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return trimmed + "/chat/completions";
    }

    private static String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= 500 ? text : text.substring(0, 500) + "...";
    }
}
