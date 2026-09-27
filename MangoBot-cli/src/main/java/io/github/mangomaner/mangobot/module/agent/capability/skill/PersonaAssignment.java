package io.github.mangomaner.mangobot.module.agent.capability.skill;

/**
 * 一条人格指派：哪个作用域当前是谁。
 *
 * @param scope       作用域（全局 / bot / 会话）
 * @param personaPath 命中的人格 skill_path
 */
public record PersonaAssignment(PersonaScope scope, String personaPath) {
}
