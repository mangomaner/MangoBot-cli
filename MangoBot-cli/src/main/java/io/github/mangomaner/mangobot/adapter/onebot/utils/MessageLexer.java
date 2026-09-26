package io.github.mangomaner.mangobot.adapter.onebot.utils;

import io.github.mangomaner.mangobot.adapter.onebot.model.segment.AtSegment;
import io.github.mangomaner.mangobot.adapter.onebot.model.segment.ImageSegment;
import io.github.mangomaner.mangobot.adapter.onebot.model.segment.OneBotMessageSegment;
import io.github.mangomaner.mangobot.adapter.onebot.model.segment.ReplySegment;
import io.github.mangomaner.mangobot.adapter.onebot.model.segment.TextSegment;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 结构化 segment 与「模型字面格式」之间的双向转换器。
 *
 * <p>模型字面格式 = 普通文本 + 标记：
 * <pre>
 *   [@123]        @ 某人（只认纯数字 qq）
 *   [图片#ab: 一只猫]  真实图片（信封形态：井号后是 fileId，冒号后是自动描述，供模型复读）
 *   [图片]        真实图片但没有 fileId / 不是信封形态（模型看到的占位标记）
 *   [表情]        QQ 表情/贴纸（只有标记，拿不到真实像素）
 *   [image=ab]    数据集存储时的图片凭证（前端靠它出缩略图）
 * </pre>
 *
 * <p>约定：
 * <ul>
 *   <li>reply 渲染成 {@code [引用#&lt;id&gt;]} 标记：它表示"这条在对着上面某句说"，
 *       是理解上下文必需的；被引用的正文不在这里展开。同时调用方仍会从 segment 里单独
 *       提出 replyToMessageId，用于判断"引的是不是本批里的某条"。</li>
 *   <li>真实上传图（image）与 QQ 表情/贴纸（face）区分：前者是 {@code [图片]} 并送像素，后者是 {@code [表情]} 只有标记。</li>
 *   <li>其它未支持 segment 类型（视频/语音等）渲染为 {@code [类型]} 占位。</li>
 *   <li>信封形态的图片带 fileId，模型据此调 {@code send_image} 复读；数据集形态给 {@code [image=file]}。</li>
 * </ul>
 */
public final class MessageLexer {

    private static final Pattern AT_TOKEN = Pattern.compile("\\[@([^\\]]*)]");

    private MessageLexer() {
    }

    public enum RenderMode {
        /** 模型可见的信封 / 风格注入：图片给 {@code [图片#<fileId>: 描述]}（id 供 send_image 复读）。 */
        ENVELOPE,
        /** 数据集存储：图片给 {@code [image=file_id]}，供前端展示。 */
        DATASET
    }

    /** 将 segment 数组渲染成模型字面文本。 */
    public static String render(List<OneBotMessageSegment> segments, RenderMode mode) {
        return render(segments, mode, null);
    }

    /**
     * 将 segment 数组渲染成模型字面文本，可按 fileId 补上图片描述。
     *
     * <p>{@code imageDescriber} 只在 {@link RenderMode#ENVELOPE} 下生效：渲染成
     * {@code [图片#<fileId>: <描述>]}，拿不到描述时是 {@code [图片#<fileId>]}，段里没有 fileId 时是
     * {@code [图片]}。传 {@code null} 时行为与两参重载完全一致（只差有没有 id）。
     * 描述本身是入站时 {@code OneBotMessageFileProcessor} 已经存好的一份（VLM 摘要），这里只是去取。
     *
     * <p>{@link RenderMode#DATASET} 分支不受影响——那里给的是给前端出缩略图用的 file 凭证。
     *
     * @param imageDescriber 入参是图片段的 file 字段（fileId），返回描述文本；可以为 null
     */
    public static String render(List<OneBotMessageSegment> segments, RenderMode mode,
                                Function<String, String> imageDescriber) {
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
                case "image" -> renderImage((ImageSegment) segment, mode, imageDescriber);
                case "face" -> "[表情]"; // QQ 表情/贴纸：只给标记（拿不到像素），与真实图片 [图片] 区分
                // 引用是"这条在对着上面某句说"的标记。渲染出来（不渲染的话，那条被引用的老消息
                // 不在本屏时，模型只看到一句没头没尾的话，会误当成新话题的开头）。
                // 那个 id 与行尾的 [消息#id] 是同一套号，所以模型能回到上面去找它引的是哪句。
                // 注意：被引用的那条<b>不在</b>本批时，它的正文不会出现在信封里，模型只能看到这个号。
                case "reply" -> renderReply((ReplySegment) segment);
                default -> renderOther(type);
            });
        }
        return result.toString();
    }

    /** 引用段：渲染成 {@code [引用#<消息id>]}；id 缺失时退化成一个光秃秃的 {@code [引用]}。 */
    private static String renderReply(ReplySegment segment) {
        String id = segment.getData() == null ? null : segment.getData().getId();
        return id == null || id.isBlank() ? "[引用]" : "[引用#" + id + "]";
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

    private static String renderImage(ImageSegment segment, RenderMode mode,
                                      Function<String, String> imageDescriber) {
        String file = segment.getData() == null ? null : segment.getData().getFile();
        if (mode == RenderMode.DATASET && file != null && !file.isEmpty()) {
            return "[image=" + file + "]";
        }
        if (mode != RenderMode.ENVELOPE) {
            return "[图片]";
        }
        if (file == null || file.isEmpty()) {
            return "[图片]";
        }

        // 信封里带上 fileId：模型据此才能把"上面那张图"原样发回去（send_image 收的就是这串 id）。
        String description = imageDescriber == null ? null : imageDescriber.apply(file);
        String cleaned = description == null ? "" : normalizeDescription(description);
        return cleaned.isEmpty() ? "[图片#" + file + "]" : "[图片#" + file + ": " + cleaned + "]";
    }

    /**
     * 把 VLM 描述压成能塞进信封的一行。
     *
     * <p>库里的描述是<b>多行</b>文本（{@code ImageParseService} 的提示词要求结构化输出），还裹着
     * {@code <image>} 与 {@code <|begin_of_box|>} 这类壳。信封的约定是「一条消息一行」，
     * 原样拼进去会把一条消息劈成看起来像好几条，模型读到的对话边界就错了。
     */
    private static String normalizeDescription(String description) {
        return description
                .replace("<|begin_of_box|>", "")
                .replace("<|end_of_box|>", "")
                .replaceAll("(?i)</?image>", "")
                .replaceAll("\\s+", " ")
                .trim();
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
