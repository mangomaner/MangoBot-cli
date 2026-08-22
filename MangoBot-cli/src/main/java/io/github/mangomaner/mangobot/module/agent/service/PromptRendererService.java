package io.github.mangomaner.mangobot.module.agent.service;

import io.github.mangomaner.mangobot.module.agent.model.enums.SessionSource;

/**
 * 三层提示词渲染 Service：将 L1/L2 规范与 L3 风格数据集拼装为追加到系统提示词末尾的 Markdown。
 */
public interface PromptRendererService {

    /**
     * 渲染指定会话的三层提示词。
     *
     * @return 无任何可注入内容时返回 null（渲染器应跳过注入）。
     */
    String render(String botId, SessionSource source, String chatId);
}
