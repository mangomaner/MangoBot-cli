package io.github.mangomaner.mangobot.module.agent.chat;

import io.agentscope.core.message.Base64Source;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.ImageBlock;
import io.agentscope.core.message.TextBlock;
import io.github.mangomaner.mangobot.adapter.onebot.model.segment.ImageSegment;
import io.github.mangomaner.mangobot.adapter.onebot.model.segment.OneBotMessageSegment;
import io.github.mangomaner.mangobot.adapter.onebot.model.segment.ReplySegment;
import io.github.mangomaner.mangobot.adapter.onebot.utils.MessageLexer;
import io.github.mangomaner.mangobot.module.file.model.domain.BotFiles;
import io.github.mangomaner.mangobot.module.file.service.BotFilesService;
import io.github.mangomaner.mangobot.utils.FileUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * 群/私聊消息信封与多模态组装：把 OneBot segments 渲染成模型字面（[@qq]/[图片]），
 * 并把真实图片字节收集为 ImageBlock 交给主模型。图片永不描述化，只以像素进模型。
 */
@Component
@RequiredArgsConstructor
public class MessageEnvelope {

    private final BotFilesService botFilesService;

    /** 正文：结构化 segments → 模型字面（ENVELOPE 模式，@→[@qq]，图→[图片]） */
    public String renderBody(List<OneBotMessageSegment> segments) {
        return MessageLexer.render(segments, MessageLexer.RenderMode.ENVELOPE);
    }

    /** 图片 segments → ImageBlock（读本地字节 base64），读不到则跳过 */
    public List<ContentBlock> imageBlocks(List<OneBotMessageSegment> segments) {
        List<ContentBlock> blocks = new ArrayList<>();
        if (segments == null) {
            return blocks;
        }
        for (OneBotMessageSegment segment : segments) {
            if (segment instanceof ImageSegment image) {
                byte[] bytes = readImageBytes(image);
                if (bytes != null) {
                    blocks.add(ImageBlock.builder()
                            .source(Base64Source.builder()
                                    .mediaType(mediaType(image))
                                    .data(Base64.getEncoder().encodeToString(bytes))
                                    .build())
                            .build());
                }
            }
        }
        return blocks;
    }

    /** 该消息内 reply 段指向的 message_id，无则 null */
    public String replyTargetId(List<OneBotMessageSegment> segments) {
        if (segments == null) {
            return null;
        }
        for (OneBotMessageSegment segment : segments) {
            if (segment instanceof ReplySegment reply && reply.getData() != null) {
                return reply.getData().getId();
            }
        }
        return null;
    }

    public ContentBlock text(String text) {
        return TextBlock.builder().text(text).build();
    }

    private byte[] readImageBytes(ImageSegment image) {
        ImageSegment.ImageData data = image.getData();
        if (data == null || !StringUtils.hasText(data.getFile())) {
            return null;
        }
        String fileName = basename(data.getFile());
        BotFiles botFile = botFilesService.getFileByFileId(fileName);
        byte[] bytes;
        if (botFile != null && StringUtils.hasText(botFile.getFilePath())) {
            bytes = readBytes(FileUtils.resolvePath(botFile.getFilePath()));
        } else {
            bytes = readBytes(FileUtils.resolvePath("data/image/" + fileName));
            if (bytes == null) {
                bytes = readBytes(FileUtils.resolvePath("data/meme/" + fileName));
            }
        }
        return bytes;
    }

    private static byte[] readBytes(Path path) {
        if (path == null || !Files.exists(path)) {
            return null;
        }
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            return null;
        }
    }

    private static String basename(String fileId) {
        int idx = Math.max(fileId.lastIndexOf('/'), fileId.lastIndexOf('\\'));
        return idx >= 0 && idx < fileId.length() - 1 ? fileId.substring(idx + 1) : fileId;
    }

    private static String mediaType(ImageSegment image) {
        String file = image.getData() == null ? null : image.getData().getFile();
        String name = basename(file).toLowerCase();
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".gif")) return "image/gif";
        if (name.endsWith(".webp")) return "image/webp";
        if (name.endsWith(".bmp")) return "image/bmp";
        return "image/jpeg";
    }
}
