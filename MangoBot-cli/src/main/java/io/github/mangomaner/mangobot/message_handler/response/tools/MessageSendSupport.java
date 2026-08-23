package io.github.mangomaner.mangobot.message_handler.response.tools;

import io.github.mangomaner.mangobot.adapter.onebot.handler.outbound.build_sending_message.OneBotSendingMessage;
import io.github.mangomaner.mangobot.adapter.onebot.model.segment.ImageSegment;
import io.github.mangomaner.mangobot.adapter.onebot.model.segment.OneBotMessageSegment;
import io.github.mangomaner.mangobot.adapter.onebot.model.segment.ReplySegment;
import io.github.mangomaner.mangobot.adapter.onebot.utils.MessageLexer;
import io.github.mangomaner.mangobot.module.file.model.domain.BotFiles;
import io.github.mangomaner.mangobot.utils.FileUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * 发送消息段构建：与具体会话来源（群/私聊）无关，只负责把模型字面或 bot_files
 * 组装成 OneBot 段列表。reply 段按 OneBot 惯例前置，图片一律读本地字节 base64 发送。
 */
final class MessageSendSupport {

    private MessageSendSupport() {}

    /** 文本消息：解析模型字面里的 [@qq]，可选前置 reply 段 */
    static OneBotSendingMessage textMessage(String message, Long replyToMessageId) {
        List<OneBotMessageSegment> segments = withReply(replyToMessageId);
        segments.addAll(MessageLexer.parse(message));
        return wrap(segments);
    }

    /** 图片消息：读 bot_files 本地字节 → base64://，可选前置 reply 段 */
    static OneBotSendingMessage imageMessage(BotFiles file, Long replyToMessageId) {
        List<OneBotMessageSegment> segments = withReply(replyToMessageId);
        segments.add(imageSegment(file));
        return wrap(segments);
    }

    private static List<OneBotMessageSegment> withReply(Long replyToMessageId) {
        List<OneBotMessageSegment> segments = new ArrayList<>();
        if (replyToMessageId != null) {
            ReplySegment reply = new ReplySegment();
            ReplySegment.ReplyData data = new ReplySegment.ReplyData();
            data.setId(String.valueOf(replyToMessageId));
            reply.setData(data);
            reply.setType("reply");
            segments.add(reply);
        }
        return segments;
    }

    private static ImageSegment imageSegment(BotFiles file) {
        ImageSegment segment = new ImageSegment();
        ImageSegment.ImageData data = new ImageSegment.ImageData();
        data.setFile("base64://" + Base64.getEncoder().encodeToString(readBytes(file)));
        data.setSubType("meme".equals(file.getFileType()) ? 1 : null);
        segment.setData(data);
        segment.setType("image");
        return segment;
    }

    private static byte[] readBytes(BotFiles file) {
        try {
            return Files.readAllBytes(FileUtils.resolvePath(file.getFilePath()));
        } catch (IOException e) {
            throw new IllegalStateException("图片文件读取失败: " + file.getFilePath(), e);
        }
    }

    private static OneBotSendingMessage wrap(List<OneBotMessageSegment> segments) {
        OneBotSendingMessage message = new OneBotSendingMessage();
        message.setMessage(segments);
        return message;
    }
}
