package io.github.mangomaner.mangobot.message_handler.response.tools;

import io.github.mangomaner.mangobot.adapter.onebot.handler.outbound.build_sending_message.OneBotSendingMessage;
import io.github.mangomaner.mangobot.annotation.MangoTool;
import io.github.mangomaner.mangobot.annotation.MangoToolMethod;
import io.github.mangomaner.mangobot.annotation.MangoToolParam;
import io.github.mangomaner.mangobot.api.MangoFileApi;
import io.github.mangomaner.mangobot.api.MangoOneBotApi;
import io.github.mangomaner.mangobot.module.agent.capability.tool.MangoToolContext;
import io.github.mangomaner.mangobot.module.agent.capability.tool.TurnContext;
import io.github.mangomaner.mangobot.module.agent.capability.tool.TurnContextRegistry;
import io.github.mangomaner.mangobot.module.agent.model.enums.SessionSource;
import io.github.mangomaner.mangobot.module.file.model.domain.BotFiles;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * bot 的「嘴」：把回复发到当前群聊/私聊。
 *
 * <p>claude 是 {@code --tools ""} 起的，它自己没有任何发送能力——不说话的唯一方式就是「不调这个工具」。
 * 这一层刻意不落库：bot 自己发的消息会作为 OneBot 事件回来，{@code MessageStoreHandler} 已经存了，
 * 这里再写一次就是重复行。
 *
 * <p>会话身份从 {@link MangoToolContext} 来（由 MCP server 在调用前从请求头取出来），
 * "往哪发、引用哪条"从 {@link TurnContextRegistry} 来（由轮次编排在开始时登记）。
 */
@MangoTool(name = "QqReplyTool", description = "把回复发送到当前对话（群聊/私聊）", category = "SYSTEM")
@Slf4j
public class QqReplyTool {

    /** 一轮最多发几条；模型偶尔会连着刷屏，这是唯一闸门。 */
    private static final int MAX_SENDS_PER_TURN = 4;

    @MangoToolMethod(name = "send_reply",
            description = "把这条消息原样发到当前群聊/私聊。想说话就调它，不说话就别调；"
                    + "一次调用发一条，想连发两条就调两次。文本里的 @某人 写成 [@QQ号]。")
    public String reply(
            @MangoToolParam(name = "text", description = "要发出去的原话") String text) {
        TurnContext context = resolveContext();
        if (text == null || text.isBlank()) {
            return "没有内容可发：text 为空。";
        }
        String rejection = acquireSendSlot(context);
        if (rejection != null) {
            return rejection;
        }

        send(context, MessageSendSupport.textMessage(text, context.replyToMessageId()));
        log.info("已发送文本回复：source={} target={} length={}",
                context.source().getSourceKey(), context.targetId(), text.length());
        return "已发送。";
    }

    @MangoToolMethod(name = "send_meme",
            description = "从表情包库挑一张最贴合 keyword 的发出去。想用表情包回应就调它，"
                    + "keyword 用一两个词描述想要的情绪或内容（如 开心 / 无语 / 摸鱼）。")
    public String sendMeme(
            @MangoToolParam(name = "keyword", description = "想要的情绪或内容，一两个词") String keyword) {
        TurnContext context = resolveContext();
        String rejection = acquireSendSlot(context);
        if (rejection != null) {
            return rejection;
        }

        BotFiles meme = pickMeme(keyword);
        if (meme == null) {
            return "表情包库里没有可用的图（需要 fileType=meme 且已生成描述），这条没有发出去，改用文字回复吧。";
        }

        send(context, MessageSendSupport.imageMessage(meme, context.replyToMessageId()));
        log.info("已发送表情包：source={} target={} keyword={} fileId={}",
                context.source().getSourceKey(), context.targetId(), keyword, meme.getFileId());
        return "已发送表情包。";
    }

    // ========================================================================
    // 内部
    // ========================================================================

    /**
     * 取当前会话 + 轮次上下文；任一缺失都返回可直接回给模型的原因。
     *
     * @throws IllegalStateException 会话或轮次缺失——由 MCP 层包成 isError 结果，模型看得到原因
     */
    private TurnContext resolveContext() {
        String sessionKey = MangoToolContext.get();
        if (sessionKey == null || sessionKey.isBlank()) {
            throw new IllegalStateException("无法确定当前会话（请求没带会话标识），这条没有发出去。");
        }
        TurnContext context = TurnContextRegistry.current(sessionKey);
        if (context == null) {
            throw new IllegalStateException("当前没有进行中的对话轮次，这条没有发出去。");
        }
        if (context.source() != SessionSource.GROUP && context.source() != SessionSource.PRIVATE) {
            throw new IllegalStateException("当前会话不是群聊或私聊（source="
                    + context.source().getSourceKey() + "），发不出去。");
        }
        return context;
    }

    /** 占一个发送名额；超限时返回该回给模型的话，否则返回 null。 */
    private String acquireSendSlot(TurnContext context) {
        if (context.sends().incrementAndGet() > MAX_SENDS_PER_TURN) {
            return "本轮发送条数已达上限（" + MAX_SENDS_PER_TURN + " 条），这条没有发出去。";
        }
        return null;
    }

    private void send(TurnContext context, OneBotSendingMessage message) {
        if (context.source() == SessionSource.GROUP) {
            MangoOneBotApi.sendGroupMsg(context.botId(), context.targetId(), message);
        } else {
            MangoOneBotApi.sendPrivateMsg(context.botId(), context.targetId(), message);
        }
    }

    /**
     * 挑一张表情包：按 keyword 与描述的命中数打分，取最高分；一条都没命中就在有描述的里随机取。
     *
     * <p>只考虑 {@code fileType=meme} <b>且描述非空</b>的行——没有描述的图发出去无法预期效果，
     * 也挑不出来。库里为空或全无描述时返回 null，由调用方回一句可读的话让模型退回文字。
     */
    private BotFiles pickMeme(String keyword) {
        List<BotFiles> usable = MangoFileApi.getFilesByType("meme").stream()
                .filter(file -> file.getDescription() != null && !file.getDescription().isBlank())
                .filter(file -> file.getFilePath() != null && !file.getFilePath().isBlank())
                .toList();
        if (usable.isEmpty()) {
            log.warn("表情包库为空或所有 meme 行都没有描述，send_meme 无法出图");
            return null;
        }

        String trimmed = keyword == null ? "" : keyword.trim();
        if (trimmed.isEmpty()) {
            return usable.get(ThreadLocalRandom.current().nextInt(usable.size()));
        }

        BotFiles best = null;
        int bestScore = 0;
        for (BotFiles file : usable) {
            int score = score(trimmed, file.getDescription());
            if (score > bestScore) {
                best = file;
                bestScore = score;
            }
        }
        return best != null ? best : usable.get(ThreadLocalRandom.current().nextInt(usable.size()));
    }

    /**
     * 命中计分：整词命中给高权重，否则按单字命中累加。
     *
     * <p>中文关键词很难靠空格切词，整词命中（"开心"出现在描述里）和单字命中（"开"/"心"）分开算，
     * 前者几乎一定相关，后者只能算弱相关。
     */
    private int score(String keyword, String description) {
        if (description.contains(keyword)) {
            return 10;
        }
        int hits = 0;
        for (int i = 0; i < keyword.length(); i++) {
            char c = keyword.charAt(i);
            if (!Character.isWhitespace(c) && description.indexOf(c) >= 0) {
                hits++;
            }
        }
        return hits;
    }
}
