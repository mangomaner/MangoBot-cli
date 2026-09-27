package io.github.mangomaner.mangobot.infra.claude.session;

import java.util.List;

/**
 * 一轮对话要送进去的内容：一段文本 + 若干张图（可为空）。
 *
 * <p>拼成 content block 时图在前、文本在后——Anthropic 推荐的顺序。图片落在提示词缓存边界之外，
 * 所以每轮都是净增输入，数量上限是唯一的成本闸门（见 {@code mangobot.claude.maxImagesPerTurn}）。
 *
 * @param text   正文（信封化的这一批消息）
 * @param images 真实像素；为空时退化成纯文本轮
 */
public record ClaudeTurnInput(String text, List<ClaudeImage> images) {

    public ClaudeTurnInput(String text) {
        this(text, List.of());
    }
}
