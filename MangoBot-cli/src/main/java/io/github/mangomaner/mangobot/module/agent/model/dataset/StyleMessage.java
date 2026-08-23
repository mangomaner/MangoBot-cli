package io.github.mangomaner.mangobot.module.agent.model.dataset;

import com.fasterxml.jackson.annotation.JsonAlias;

/**
 * 风格数据集消息（内嵌 JSON 存储于 style_dataset.messages）。
 *
 * <p>content 用「模型字面格式」（{@code [\@qq]} / {@code [image=file_id]}），回复用结构字段 replyToIndex
 * 指向本数据集 messages 数组内被回复消息的下标（0 起始，可指任意一条、不限上一条；null = 非回复）。
 * 兼容读取旧数据：缺省 content 时回退到 text 字段。
 *
 * @param roleName     所属角色名（须存在于 roles）
 * @param content      消息文本（模型字面格式；DATASET 渲染）
 * @param replyToIndex 被回复消息下标；null = 非回复
 */
public record StyleMessage(
        String roleName,
        @JsonAlias("text") String content,
        Integer replyToIndex) {
}
