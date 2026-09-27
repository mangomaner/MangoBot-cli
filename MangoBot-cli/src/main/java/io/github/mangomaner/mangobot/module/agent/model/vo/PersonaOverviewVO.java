package io.github.mangomaner.mangobot.module.agent.model.vo;

import io.github.mangomaner.mangobot.module.agent.model.domain.AgentSkillConfig;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 人格页的一次拉取结果：全部人格 + 每个场景当前命中的那个人格。
 *
 * <p>{@code assignment} 的键是来源 key（web / group / private），值是当前该场景指派的那个
 * 人格的 skill 路径；没指派则为 null。前端据此渲染"每个场景选了谁"，派发时也用它对上号。
 */
@Data
public class PersonaOverviewVO {

    /** 全部人格 skill */
    private List<SkillVO> personas;

    /** 来源 key → 当前指派的人格路径（可能为 null） */
    private Map<String, String> assignment;

    public static PersonaOverviewVO of(List<AgentSkillConfig> personas, Map<String, String> assignment) {
        PersonaOverviewVO vo = new PersonaOverviewVO();
        vo.setPersonas(personas.stream().map(SkillVO::from).toList());
        vo.setAssignment(assignment);
        return vo;
    }
}