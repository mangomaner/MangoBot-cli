package io.github.mangomaner.mangobot.module.agent.model.vo;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mangomaner.mangobot.module.agent.capability.skill.SkillLibrary;
import io.github.mangomaner.mangobot.module.agent.model.domain.AgentSkillConfig;
import io.github.mangomaner.mangobot.module.agent.model.enums.SessionSource;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;

import java.nio.file.Files;
import java.util.ArrayList;
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

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static SkillVO from(AgentSkillConfig config) {
        SkillVO vo = new SkillVO();
        BeanUtils.copyProperties(config, vo);
        vo.setAvailable(Files.exists(SkillLibrary.skillMd(config.getSkillPath())));

        // 解析 JSON 字符串为 List<SessionSource>
        vo.setEnabledList(parseSourceList(config.getEnabledList()));
        vo.setAvailableList(parseSourceList(config.getAvailableList()));

        return vo;
    }

    /**
     * 解析来源列表 JSON 字符串。
     *
     * <p>认不出的键（人格的作用域键：{@code 1389:group}）<b>逐个跳过</b>而不是整列作废——
     * 旧实现把整段解析包在一个 try 里，而 {@code SessionSource.fromKey} 对未知键是抛异常的，
     * 于是一条作用域键就让整列变空。人格的指派不走这里，走 {@code PersonaOverviewVO.assignments}。
     */
    private static List<SessionSource> parseSourceList(String json) {
        if (json == null || json.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> sourceKeys;
        try {
            sourceKeys = objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse source list: {}", json, e);
            return Collections.emptyList();
        }
        List<SessionSource> sources = new ArrayList<>();
        for (String key : sourceKeys) {
            try {
                sources.add(SessionSource.fromKey(key));
            } catch (IllegalArgumentException ignored) {
                // 作用域键走到这里是正常的，不是错误
            }
        }
        return sources;
    }
}
