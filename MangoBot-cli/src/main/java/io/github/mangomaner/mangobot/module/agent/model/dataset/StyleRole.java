package io.github.mangomaner.mangobot.module.agent.model.dataset;

/**
 * 风格数据集角色（内嵌 JSON 存储于 style_dataset.roles）。
 *
 * <p>角色不区分机器人/群友，统一视为对话剧本中的普通发言角色。
 *
 * @param name 角色名
 */
public record StyleRole(String name) {
}
