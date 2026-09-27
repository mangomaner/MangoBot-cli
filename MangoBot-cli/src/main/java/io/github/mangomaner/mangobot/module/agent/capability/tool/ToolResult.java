package io.github.mangomaner.mangobot.module.agent.capability.tool;

import java.util.List;

/**
 * 工具返回值的富形态：文字 + 若干张图。
 *
 * <p>{@code @MangoToolMethod} 的方法可以直接返回 {@code String}（老样子，会被原样当文本），
 * 也可以返回本类型。文字和图片**可以同时有**，这不是多余的——真正要用到这个形状的场景是
 * "给模型看几张候选图，同时告诉它每张的 id"：图用来判断，文字用来给它一个能引用的句柄。
 *
 * <p>为什么不让工具直接返回 MCP 的 {@code McpSchema.ImageContent}：那会把 SDK 类型泄进
 * 工具代码里，写工具的人不该知道 MCP 长什么样。
 */
public record ToolResult(String text, List<ToolImage> images) {

    /** 纯文字，等价于直接返回 String。 */
    public static ToolResult text(String text) {
        return new ToolResult(text, List.of());
    }

    /** 只有图，没有配字。 */
    public static ToolResult image(String mimeType, byte[] data) {
        return new ToolResult(null, List.of(new ToolImage(mimeType, data)));
    }

    public static ToolResult of(String text, List<ToolImage> images) {
        return new ToolResult(text, images == null ? List.of() : List.copyOf(images));
    }
}
