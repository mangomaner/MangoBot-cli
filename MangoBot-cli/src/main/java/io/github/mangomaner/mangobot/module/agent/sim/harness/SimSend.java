package io.github.mangomaner.mangobot.module.agent.sim.harness;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * bot 在一次模拟对话里真实发出去的一条消息。
 *
 * <p>不是从发送出口"拦截"下来的意图，而是从<b>出站 WebSocket 帧</b>还原出来的既成事实——
 * bot 调了发送工具、帧真的出了 socket、模拟客户端把它记了下来。所以形状完全来自协议本身：
 * {@code kind} 只有 {@code text} / {@code image} / {@code meme} 三种（帧里只有这些段），
 * {@code payload} 对文本是正文原文、对图片是还原出来的 fileId。
 *
 * @param kind      {@code text}（{@code send_reply}）/ {@code meme}（{@code send_meme}）/ {@code image}（{@code send_image}）
 * @param payload   文本正文 / 表情包关键词 / 图片 fileId
 * @param replyToId 这条引用了哪条消息；没引用则为 null
 */
public record SimSend(String kind, String payload, String replyToId) {

    /**
     * 是文字回复吗——只有文字才有长度、标点这些可谈的。
     *
     * <p><b>{@code @JsonIgnore} 是必须的</b>：这个 {@code is} 前缀让 Jackson 认成属性 {@code text}，
     * 于是序列化会多写一个字段、而反序列化回来会因为"没有这个 record 组件"直接抛
     * UnrecognizedPropertyException。派生值不入盘，由 {@code kind} 现算即可。
     */
    @JsonIgnore
    public boolean isText() {
        return "text".equals(kind);
    }
}
