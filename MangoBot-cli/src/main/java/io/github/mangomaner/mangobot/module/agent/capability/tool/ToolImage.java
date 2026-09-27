package io.github.mangomaner.mangobot.module.agent.capability.tool;

/**
 * 工具要回给模型的一张图。
 *
 * <p>和 {@code ClaudeImage}（那一头是"每轮附带送进去的入站图"）是两回事：这个是**工具结果**里带的图，
 * 走的是 MCP 的 {@code CallToolResult}，不是往 claude 进程写 stdio。两条路径互不相干，
 * 一条通不代表另一条通（所以才有这个类）。
 *
 * @param mimeType  MIME 类型，如 {@code image/png}
 * @param data      图片原始字节；base64 编码在包成 MCP content 时做
 */
public record ToolImage(String mimeType, byte[] data) {
}
