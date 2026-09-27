package io.github.mangomaner.mangobot.module.agent.model.vo;

import io.github.mangomaner.mangobot.module.agent.capability.skill.PersonaAssignment;
import io.github.mangomaner.mangobot.module.agent.capability.skill.PersonaScope;
import io.github.mangomaner.mangobot.module.agent.model.domain.AgentSkillConfig;
import lombok.Data;

import java.util.List;

/**
 * 人格页的一次拉取结果：全部人格 + 每一级作用域当前命中的那个人格。
 *
 * <p>指派用 {@code source / botId / targetId} 三元组表达，<b>不下发作用域键的字符串格式</b>——
 * 键怎么拼是后端的事，前端不该跟着学一套（它只需要知道"哪个场景、哪个 bot、哪个会话"）。
 * botId 为空即全局；targetId 为空即非会话级。
 */
@Data
public class PersonaOverviewVO {

    /** 全部人格 skill */
    private List<SkillVO> personas;

    /** 当前生效的全部指派（没被指派的作用域不出现） */
    private List<AssignmentVO> assignments;

    @Data
    public static class AssignmentVO {
        private String source;
        private String botId;
        private String targetId;
        private String personaPath;
    }

    public static PersonaOverviewVO of(List<AgentSkillConfig> personas, List<PersonaAssignment> assignments) {
        PersonaOverviewVO vo = new PersonaOverviewVO();
        vo.setPersonas(personas.stream().map(SkillVO::from).toList());
        vo.setAssignments(assignments.stream().map(PersonaOverviewVO::toVO).toList());
        return vo;
    }

    private static AssignmentVO toVO(PersonaAssignment assignment) {
        PersonaScope scope = assignment.scope();
        AssignmentVO item = new AssignmentVO();
        item.setSource(scope.source().getSourceKey());
        item.setBotId(scope.botId());
        item.setTargetId(scope.targetId());
        item.setPersonaPath(assignment.personaPath());
        return item;
    }
}
