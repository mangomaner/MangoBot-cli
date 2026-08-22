package io.github.mangomaner.mangobot.module.agent.core;

import io.github.mangomaner.mangobot.module.agent.model.enums.PromptLayer;
import io.github.mangomaner.mangobot.module.agent.model.enums.SessionSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 出厂提示词模板加载器：读取 resources/prompt_norm/&lt;source&gt;/&lt;layer&gt;.md。
 *
 * <p>模板作为 GLOBAL 之上的继承链地板（TEMPLATE），不播种 DB；加载后按 (source,layer) 缓存。
 */
@Slf4j
@Component
public class PromptTemplateLoader {

    private static final String BASE_PATH = "prompt_norm/%s/%s.md";

    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    /** 读取出厂模板；缺失时返回 null（渲染器跳过该层）。 */
    public String load(SessionSource source, PromptLayer layer) {
        return cache.computeIfAbsent(key(source, layer), k -> readTemplate(source, layer));
    }

    private String readTemplate(SessionSource source, PromptLayer layer) {
        try {
            ClassPathResource resource = new ClassPathResource(String.format(BASE_PATH, source.getSourceKey(), layer.getCode()));
            if (!resource.exists()) {
                log.warn("Prompt template not found: {}/{}", source.getSourceKey(), layer.getCode());
                return null;
            }
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            log.error("Failed to load prompt template: {}/{}", source.getSourceKey(), layer.getCode(), e);
            return null;
        }
    }

    private static String key(SessionSource source, PromptLayer layer) {
        return source.getSourceKey() + "/" + layer.getCode();
    }
}
