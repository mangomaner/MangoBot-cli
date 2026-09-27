package io.github.mangomaner.mangobot.infra.claude.session;

/**
 * 要送给 claude 的一张图，已是可直接进 content block 的形状。
 *
 * <p>实测（claude v2.1.128）：{@code --input-format stream-json} 接受
 * {@code {"type":"image","source":{"type":"base64","media_type":...,"data":...}}}，
 * 且主模型能真的看到图。一张 1.5KB 的小图边际成本约 +250 输入 token（随尺寸与模型变）。
 *
 * @param mediaType  MIME 类型，如 {@code image/jpeg}
 * @param base64Data 图片字节的 base64（不含 data URL 前缀）
 */
public record ClaudeImage(String mediaType, String base64Data) {
}
