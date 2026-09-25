package io.github.mangomaner.mangobot.module.agent.chat;

import io.github.mangomaner.mangobot.module.file.model.domain.BotFiles;

import java.util.List;

/**
 * 一条入站消息：已经信封化的文本 + 它带的图。
 *
 * <p>文本在监听器里就渲染好了（那时才拿得到发送者、时间与图片描述），轮次编排只负责拼装。
 * 图片在这里带的是<b>查好的</b> {@link BotFiles} 实体，不是 fileId——监听器为每个 image 段只查一次库，
 * 同一份结果既喂了文本里的描述，也喂了后面读字节送像素那一步。
 *
 * @param messageId        本条消息 id（去重、以及被当作回复目标时用）
 * @param replyToMessageId 本条消息回复的那条 id，没回复则 null
 * @param text             信封行，形如 {@code [2026-09-25 14:03:11] 张三(123456): 你好啊}
 * @param images           本条消息里的真实图片（已按段顺序、按 fileId 去重）
 */
public record IncomingLine(long messageId, Long replyToMessageId, String text, List<BotFiles> images) {

    public IncomingLine {
        images = images == null ? List.of() : List.copyOf(images);
    }
}
