package io.github.mangomaner.mangobot.module.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mangomaner.mangobot.module.agent.capability.skill.SkillLibrary;
import io.github.mangomaner.mangobot.module.agent.model.domain.AgentSkillConfig;
import io.github.mangomaner.mangobot.module.agent.model.enums.SessionSource;
import io.github.mangomaner.mangobot.module.agent.service.AgentSkillConfigService;
import io.github.mangomaner.mangobot.system.mapper.agent.AgentSkillConfigMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class AgentSkillConfigServiceImpl extends ServiceImpl<AgentSkillConfigMapper, AgentSkillConfig>
    implements AgentSkillConfigService{

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public AgentSkillConfig getBySkillPath(String skillPath) {
        return getOne(new LambdaQueryWrapper<AgentSkillConfig>()
                .eq(AgentSkillConfig::getSkillPath, skillPath));
    }

    @Override
    public List<AgentSkillConfig> listEnabled() {
        return list(new LambdaQueryWrapper<AgentSkillConfig>()
                .eq(AgentSkillConfig::getEnabled, true));
    }

    @Override
    public List<AgentSkillConfig> listSkills() {
        return listByKind(SkillLibrary.Kind.SKILL);
    }

    @Override
    public List<AgentSkillConfig> listPersonas() {
        return listByKind(SkillLibrary.Kind.PERSONA);
    }

    /** 类型由 skill_path 的前缀决定，所以在 Java 侧筛；表里就几行，不值得为它写 SQL 转义。 */
    private List<AgentSkillConfig> listByKind(SkillLibrary.Kind kind) {
        return list().stream()
                .filter(config -> SkillLibrary.kindOf(config.getSkillPath()) == kind)
                .toList();
    }

    @Override
    public void updateEnabledList(Integer id, List<SessionSource> enabledList) {
        AgentSkillConfig config = getById(id);
        if (config == null) {
            throw new IllegalArgumentException("Skill not found: " + id);
        }

        // 验证 enabledList 是 availableList 的子集
        List<String> availableSources = parseSourceList(config.getAvailableList());
        List<String> enabledSources = enabledList.stream()
                .map(SessionSource::getSourceKey)
                .toList();

        if (!availableSources.containsAll(enabledSources)) {
            throw new IllegalArgumentException("enabledList must be a subset of availableList");
        }

        try {
            String enabledListJson = objectMapper.writeValueAsString(enabledSources);
            config.setEnabledList(enabledListJson);
            updateById(config);
            log.info("Updated enabled list for skill {}: {}", id, enabledListJson);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize enabled list", e);
        }
    }

    private List<String> parseSourceList(String json) {
        if (json == null || json.isEmpty()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, List.class);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse source list: {}", json, e);
            return List.of();
        }
    }
}
