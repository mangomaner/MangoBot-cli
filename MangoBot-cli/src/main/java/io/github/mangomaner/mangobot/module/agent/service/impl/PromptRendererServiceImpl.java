package io.github.mangomaner.mangobot.module.agent.service.impl;

import io.github.mangomaner.mangobot.module.agent.model.dataset.StyleMessage;
import io.github.mangomaner.mangobot.module.agent.model.enums.PromptLayer;
import io.github.mangomaner.mangobot.module.agent.model.enums.SessionSource;
import io.github.mangomaner.mangobot.module.agent.model.vo.StyleDatasetDetailVO;
import io.github.mangomaner.mangobot.module.agent.model.vo.StyleDatasetRefVO;
import io.github.mangomaner.mangobot.module.agent.model.vo.StyleInjectionVO;
import io.github.mangomaner.mangobot.module.agent.service.PromptNormService;
import io.github.mangomaner.mangobot.module.agent.service.PromptRendererService;
import io.github.mangomaner.mangobot.module.agent.service.StyleDatasetService;
import io.github.mangomaner.mangobot.module.agent.service.StyleInjectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 三层提示词渲染 Service 实现。
 *
 * <p>输出格式：
 * <pre>
 * ## 系统提示词补充（分层规范）
 * ### 一、框架规范
 * {framework}
 * ### 二、人格规范
 * {persona}
 * ### 三、行为规范
 * {behavior}
 * ### 四、风格示范（对话剧本）
 * 数据集「群聊热闹风格」
 * 角色：群友A、群友B
 * 对话示例：
 * 群友A: ...
 * 群友B: ...
 * </pre>
 * 全空时返回 null，由中间件跳过注入。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromptRendererServiceImpl implements PromptRendererService {

    /** 风格示范最多渲染的消息总条数（防止提示词超长） */
    private static final int MAX_STYLE_MESSAGES = 200;

    private final PromptNormService promptNormService;
    private final StyleInjectionService styleInjectionService;
    private final StyleDatasetService styleDatasetService;

    @Override
    public String render(String botId, SessionSource source, String chatId) {
        if (source == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        appendLayer(sb, "一、框架规范", promptNormService.resolveContent(botId, source, chatId, PromptLayer.FRAMEWORK));
        appendLayer(sb, "二、人格规范", promptNormService.resolveContent(botId, source, chatId, PromptLayer.PERSONA));
        appendLayer(sb, "三、行为规范", promptNormService.resolveContent(botId, source, chatId, PromptLayer.BEHAVIOR));
        appendStyleScript(sb, botId, source, chatId);
        if (sb.length() == 0) {
            return null;
        }
        return "## 规范\n" + sb;
    }

    private void appendLayer(StringBuilder sb, String title, String content) {
        if (!StringUtils.hasText(content)) {
            return;
        }
        sb.append("\n### ").append(title).append("\n").append(content.trim()).append("\n");
    }

    /** 仅群聊/私聊注入风格示范，可叠加多个数据集 */
    private void appendStyleScript(StringBuilder sb, String botId, SessionSource source, String chatId) {
        if (source == SessionSource.WEB) {
            return;
        }
        StyleInjectionVO injection = styleInjectionService.getEffective(botId, source.getSourceKey(), chatId);
        if (injection.getDatasets() == null || injection.getDatasets().isEmpty()) {
            return;
        }

        sb.append("\n### 四、风格示范（模仿以下对话的风格与口吻来回复）\n");
        int budget = MAX_STYLE_MESSAGES;
        for (StyleDatasetRefVO ref : injection.getDatasets()) {
            if (budget <= 0) {
                break;
            }
            StyleDatasetDetailVO dataset = styleDatasetService.getDetail(ref.getDatasetId());
            if (dataset.getRoles().isEmpty() || dataset.getMessages().isEmpty()) {
                continue;
            }
            List<StyleMessage> messages = dataset.getMessages();
            int take = Math.min(messages.size(), budget);
            List<StyleMessage> slice = messages.subList(messages.size() - take, messages.size());
            budget -= take;

            sb.append("数据集「").append(dataset.getName()).append("」\n")
                    .append("角色：").append(dataset.getRoles().stream()
                            .map(role -> role.name())
                            .collect(Collectors.joining("、"))).append("\n")
                    .append("对话示例：\n");
            for (StyleMessage message : slice) {
                sb.append(message.roleName()).append(": ").append(message.text()).append("\n");
            }
        }
    }
}
