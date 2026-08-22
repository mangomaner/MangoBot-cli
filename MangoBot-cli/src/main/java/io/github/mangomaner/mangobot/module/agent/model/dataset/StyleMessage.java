package io.github.mangomaner.mangobot.module.agent.model.dataset;

/**
 * 风格数据集消息（内嵌 JSON 存储于 style_dataset.messages）。
 *
 * @param roleName 所属角色名（须存在于 roles）
 * @param text     消息文本
 */
public record StyleMessage(String roleName, String text) {
}
