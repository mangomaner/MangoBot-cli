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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
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
 *   （回复）
 * （若你要回复某人，用上方对话里对应该消息的 message_id 填入 replyToMessageId。）
 * </pre>
 * 全空时返回 null，由中间件跳过注入。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromptRendererServiceImpl implements PromptRendererService {

    /** 风格示范最多渲染的消息总条数（防止提示词超长、稀释重点） */
    private static final int MAX_STYLE_MESSAGES = 40;

    /** 数据集存储的图片凭证标记 [image=file_id]，对模型无意义，渲染时抹成 [图片]。 */
    private static final Pattern IMAGE_TOKEN = Pattern.compile("\\[image=[^]]*]");

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
        sb.append("\n重点注意：模仿「聊天最频繁的那个人」的回复节奏——一次发几条、还是文字+表情包混排。示范里的 [图片] 多是表情包。别总是只发一条纯文字。\n");
        int budget = MAX_STYLE_MESSAGES;
        boolean hadReply = false;
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
            // 同一个人连续发的多条归并成一次"连发"，示范"一次回复发多条 / 文字+表情包一起发"。
            // 之前逐行罗列，模型看不出这些属于同一次回复，只会单发纯文字。
            List<StyleMessage> turn = new ArrayList<>();
            String lastRole = null;
            for (StyleMessage message : slice) {
                String role = message.roleName();
                if (lastRole != null && !lastRole.equals(role)) {
                    hadReply |= appendTurn(sb, turn);
                    turn = new ArrayList<>();
                }
                lastRole = role;
                turn.add(message);
            }
            hadReply |= appendTurn(sb, turn);
        }
        if (hadReply) {
            String replyTool = source == SessionSource.GROUP ? "sendGroupReply" : "sendPrivateReply";
            sb.append("（默认用普通消息工具延续对话即可；仅当要明确回复的是较久之前、距当前消息较远的某条消息时，才用 ")
                    .append(replyTool)
                    .append("，并把那条的 message_id 填入 replyToMessageId。）\n");
        }
    }

    /**
     * 渲染"同一个人连续发的一组消息"，作为一次连发示范。
     * 一次回复是多条、或文字+表情包混排时，加一行注解点明多发节奏，让模型学会这么做。
     * 返回本组是否含回复标记（用于末尾回复工具的提示）。
     */
    private static boolean appendTurn(StringBuilder sb, List<StyleMessage> turn) {
        if (turn.isEmpty()) {
            return false;
        }
        String role = turn.get(0).roleName();
        boolean hasImage = false;
        boolean anyReply = false;
        for (StyleMessage m : turn) {
            String raw = m.content() == null ? "" : m.content();
            if (raw.contains("image=")) {
                hasImage = true;
            }
            if (m.replyToIndex() != null) {
                anyReply = true;
            }
            sb.append(role).append(": ").append(IMAGE_TOKEN.matcher(raw).replaceAll("[图片]")).append("\n");
            if (m.replyToIndex() != null) {
                sb.append("  （回复）\n");
            }
        }
        if (turn.size() > 1) {
            sb.append("  ↑ TA 这次一口气连发 ").append(turn.size())
                    .append(" 条（").append(hasImage ? "文字+表情包" : "纯文字")
                    .append("）：你可以学这种一次多发的节奏——连续调用发送工具发几条，或一条文字+一张表情包一起发。\n");
        } else if (turn.size() == 1) {
            String raw = turn.get(0).content() == null ? "" : turn.get(0).content();
            boolean hasText = IMAGE_TOKEN.matcher(raw).replaceAll("").trim().length() > 0;
            if (hasImage && hasText) {
                sb.append("  （这条同时带了文字和表情包）\n");
            }
        }
        return anyReply;
    }
}
