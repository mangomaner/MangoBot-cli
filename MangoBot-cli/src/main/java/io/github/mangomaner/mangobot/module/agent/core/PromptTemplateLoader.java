package io.github.mangomaner.mangobot.module.agent.core;

import io.github.mangomaner.mangobot.module.agent.model.enums.BotType;
import io.github.mangomaner.mangobot.module.agent.model.enums.PromptLayer;
import io.github.mangomaner.mangobot.module.agent.model.enums.SessionSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 出厂提示词模板加载器：读取 resources/prompt_norm/&lt;source&gt;/&lt;layer&gt;.md（框架规范），
 * 或 &lt;source&gt;/&lt;botType&gt;/&lt;layer&gt;.md（人格/行为按 bot 类型）。
 *
 * <p>模板作为 GLOBAL 之上的继承链地板（TEMPLATE），不播种 DB；加载后按完整 path 缓存（source/类型/层 天然隔离）。
 */
@Slf4j
@Component
public class PromptTemplateLoader {

    private static final String BASE_PATH = "prompt_norm/%s/%s.md";

    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    /** 读取出厂模板（默认模仿人类）；缺失时返回 null（渲染器跳过该层）。 */
    public String load(SessionSource source, PromptLayer layer) {
        return load(source, layer, BotType.defaultType());
    }

    /**
     * 按 bot 类型读取出厂模板；缺失时返回 null。
     *
     * <p>目录选择：框架规范任意类型共用 <code>prompt_norm/&lt;source&gt;/&lt;layer&gt;.md</code>；
     * 人格/行为统一读 <code>prompt_norm/&lt;source&gt;/&lt;botType&gt;/&lt;layer&gt;.md</code>（human/roleplay/instruction 各一目录）。
     */
    public String load(SessionSource source, PromptLayer layer, BotType botType) {
        String dir = layer == PromptLayer.FRAMEWORK
                ? source.getSourceKey()
                : source.getSourceKey() + "/" + botType.getCode();
        String path = String.format(BASE_PATH, dir, layer.getCode());
        return cache.computeIfAbsent(path, key -> readTemplate(path));
    }

    private String readTemplate(String path) {
        try {
            ClassPathResource resource = new ClassPathResource(path);
            if (!resource.exists()) {
                log.warn("Prompt template not found: {}", path);
                return null;
            }
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            log.error("Failed to load prompt template: {}", path, e);
            return null;
        }
    }
}
