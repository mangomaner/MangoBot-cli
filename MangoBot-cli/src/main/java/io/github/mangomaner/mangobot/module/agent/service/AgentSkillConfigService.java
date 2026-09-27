package io.github.mangomaner.mangobot.module.agent.service;

import io.github.mangomaner.mangobot.module.agent.model.domain.AgentSkillConfig;
import io.github.mangomaner.mangobot.module.agent.model.enums.SessionSource;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface AgentSkillConfigService extends IService<AgentSkillConfig> {

    AgentSkillConfig getBySkillPath(String skillPath);

    List<AgentSkillConfig> listEnabled();

    /**
     * 列出普通技能（非人格 skill），供「技能」页使用。
     */
    List<AgentSkillConfig> listSkills();

    /**
     * 列出人格 skill，供「人格」页使用。
     */
    List<AgentSkillConfig> listPersonas();

    /**
     * 更新 Skill 的启用来源列表
     *
     * @param id          Skill ID
     * @param enabledList 启用的来源列表
     */
    void updateEnabledList(Integer id, List<SessionSource> enabledList);
}
