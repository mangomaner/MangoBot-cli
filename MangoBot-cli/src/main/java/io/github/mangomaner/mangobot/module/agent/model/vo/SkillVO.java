package io.github.mangomaner.mangobot.module.agent.model.vo;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mangomaner.mangobot.module.agent.model.domain.AgentSkillConfig;
import io.github.mangomaner.mangobot.module.agent.model.enums.SessionSource;
import io.github.mangomaner.mangobot.utils.FileUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

/**
 * Skill VO
 *
 * @see AgentSkillConfig
 */
@Data
@Slf4j
public class SkillVO {
    private Integer id;
    private String skillName;
    private String description;
    private String skillPath;
    private String boundToolIds;
    private Boolean enabled;
    private Boolean available;
    private List<SessionSource> enabledList;
    private List<SessionSource> availableList;

    private static final String SKILLS_DIR = "data/capabilities/skills/skills";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static SkillVO from(AgentSkillConfig config) {
        SkillVO vo = new SkillVO();
        BeanUtils.copyProperties(config, vo);
        Path skillMdPath = FileUtils.resolvePath(SKILLS_DIR)
            .resolve(config.getSkillPath())
            .resolve("SKILL.md");
        vo.setAvailable(Files.exists(skillMdPath));
        
        // 解析 JSON 字符串为 List<SessionSource>
        vo.setEnabledList(parseSourceList(config.getEnabledList()));
        vo.setAvailableList(parseSourceList(config.getAvailableList()));
        
        return vo;
    }

    /**
     * 解析来源列表 JSON 字符串
     */
    private static List<SessionSource> parseSourceList(String json) {
        if (json == null || json.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            List<String> sourceKeys = objectMapper.readValue(json, new TypeReference<List<String>>() {});
            return sourceKeys.stream()
                    .map(SessionSource::fromKey)
                    .filter(source -> source != null)
                    .toList();
        } catch (Exception e) {
            log.warn("Failed to parse source list: {}", json, e);
            return Collections.emptyList();
        }
    }
}
