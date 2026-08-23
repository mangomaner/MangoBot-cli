package io.github.mangomaner.mangobot.adapter.onebot.utils;

import io.github.mangomaner.mangobot.adapter.onebot.model.segment.AtSegment;
import io.github.mangomaner.mangobot.adapter.onebot.model.segment.ImageSegment;
import io.github.mangomaner.mangobot.adapter.onebot.model.segment.OneBotMessageSegment;
import io.github.mangomaner.mangobot.adapter.onebot.model.segment.TextSegment;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 结构化 segment 与「模型字面格式」之间的双向转换器。
 *
 * <p>模型字面格式 = 普通文本 + 标记：
 * <pre>
 *   [@123]        @ 某人（只认纯数字 qq）
 *   [图片]        真实图片（模型看到的占位标记，真实像素由上层单独送达）
 *   [表情]        QQ 表情/贴纸（只有标记，拿不到真实像素）
 *   [image=ab]    数据集存储时的图片凭证（前端靠它出缩略图）
 * </pre>
 *
 * <p>约定：
 * <ul>
 *   <li>reply 不渲染进正文，由调用方从 segment 中单独提取（作为 replyToMessageId / replyToIndex）。</li>
 *   <li>真实上传图（image）与 QQ 表情/贴纸（face）区分：前者是 {@code [图片]} 并送像素，后者是 {@code [表情]} 只有标记。</li>
 *   <li>其它未支持 segment 类型（视频/语音等）渲染为 {@code [类型]} 占位。</li>
 *   <li>图片不写入任何文字描述，只针对不同方向给出 {@code [图片]} 或 {@code [image=file]} 标记。</li>
 * </ul>
 */
public final class MessageLexer {

    private static final Pattern AT_TOKEN = Pattern.compile("\\[@([^\\]]*)]");

    private MessageLexer() {
    }

    public enum RenderMode {
        /** 模型可见的信封 / 风格注入：图片只给 {@code [图片]} 占位。 */
        ENVELOPE,
        /** 数据集存储：图片给 {@code [image=file_id]}，供前端展示。 */
        DATASET
    }

    /** 将 segment 数组渲染成模型字面文本。 */
    public static String render(List<OneBotMessageSegment> segments, RenderMode mode) {
        if (segments == null) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        for (OneBotMessageSegment segment : segments) {
            if (segment == null) {
                continue;
            }
            String type = segment.getType();
            result.append(switch (type == null ? "" : type) {
                case "text" -> renderText((TextSegment) segment);
                case "at" -> renderAt((AtSegment) segment);
                case "image" -> renderImage((ImageSegment) segment, mode);
                case "face" -> "[表情]"; // QQ 表情/贴纸：只给标记（拿不到像素），与真实图片 [图片] 区分
                case "reply" -> ""; // reply 由调用方单独处理
                default -> renderOther(type);
            });
        }
        return result.toString();
    }

    /** 将模型字面文本解析成 segment 数组（发送方向）。只认 {@code [@数字]}，非数字抛 {@link MessageLexerException}。 */
    public static List<OneBotMessageSegment> parse(String message) {
        List<OneBotMessageSegment> segments = new ArrayList<>();
        if (message == null || message.isEmpty()) {
            return segments;
        }
        Matcher matcher = AT_TOKEN.matcher(message);
        int cursor = 0;
        while (matcher.find()) {
            String qq = matcher.group(1).trim();
            if (qq.isEmpty() || !qq.matches("\\d+")) {
                throw new MessageLexerException("非法的 @ 标记：" + matcher.group() + "，请改用 [@QQ号]（QQ 号取信封里该发送者括号中的值）。");
            }
            addText(segments, message, cursor, matcher.start());
            segments.add(atSegment(qq));
            cursor = matcher.end();
        }
        addText(segments, message, cursor, message.length());
        return segments;
    }

    private static void addText(List<OneBotMessageSegment> segments, String message, int from, int to) {
        if (to > from) {
            segments.add(textSegment(message.substring(from, to)));
        }
    }

    private static String renderText(TextSegment segment) {
        return segment.getText() == null ? "" : segment.getText();
    }

    private static String renderAt(AtSegment segment) {
        AtSegment.AtData data = segment.getData();
        if (data == null || data.getQq() == null) {
            return "";
        }
        return "[@" + data.getQq() + "]";
    }

    private static String renderImage(ImageSegment segment, RenderMode mode) {
        String file = segment.getData() == null ? null : segment.getData().getFile();
        if (mode == RenderMode.DATASET && file != null && !file.isEmpty()) {
            return "[image=" + file + "]";
        }
        return "[图片]";
    }

    private static String renderOther(String type) {
        return "[" + type + "]";
    }

    private static TextSegment textSegment(String text) {
        TextSegment segment = new TextSegment();
        TextSegment.TextData data = new TextSegment.TextData();
        data.setText(text);
        segment.setData(data);
        segment.setType("text");
        return segment;
    }

    private static AtSegment atSegment(String qq) {
        AtSegment segment = new AtSegment();
        AtSegment.AtData data = new AtSegment.AtData();
        data.setQq(qq);
        segment.setData(data);
        segment.setType("at");
        return segment;
    }
}
