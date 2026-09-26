package io.github.mangomaner.mangobot.adapter.onebot.utils;

import io.github.mangomaner.mangobot.adapter.onebot.model.segment.OneBotMessageSegment;
import io.github.mangomaner.mangobot.adapter.onebot.model.segment.ReplySegment;
import io.github.mangomaner.mangobot.adapter.onebot.model.segment.TextSegment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 信封上两个记号：行尾的 {@code [消息#id]} 与引用段的 {@code [引用#id]}。
 *
 * <p>它们不是装饰，是模型能不能"引用某句"的全部依据：没有行尾那个 id，模型就只能引用刚喂进来的
 * 最后一条——而实测真人引用紧邻上一条只占 9.7%，绝大多数引用指向被刷上去好几句的某条。
 * 所以这里把格式钉住，改坏了要立刻红。
 */
class EnvelopeMarkersTest {

    @Test
    @DisplayName("行尾带 [消息#id]，模型才有东西可以交给 reply_to")
    void lineCarriesMessageId() {
        String line = ChatEnvelope.render("2026-09-26 12:31:05", "阿岩", 1001L, "今天面试", 1386042478L);
        assertEquals("[2026-09-26 12:31:05] 阿岩(1001): 今天面试 [消息#1386042478]", line);
    }

    @Test
    @DisplayName("没有 id 时不写记号（离线构造/测试用）")
    void lineWithoutIdHasNoMarker() {
        String line = ChatEnvelope.render("12:31", "阿岩", null, "在吗");
        assertEquals("[12:31] 阿岩: 在吗", line);
        assertFalse(line.contains("消息#"));
    }

    @Test
    @DisplayName("引用段渲染成 [引用#id]，且不影响正文")
    void replySegmentRendersMarker() {
        List<OneBotMessageSegment> segments = new ArrayList<>();
        ReplySegment reply = new ReplySegment();
        ReplySegment.ReplyData data = new ReplySegment.ReplyData();
        data.setId("1512804543");
        reply.setData(data);
        reply.setType("reply");   // type 由 Jackson 反序列化时填；手工构造要自己设
        segments.add(reply);
        TextSegment text = new TextSegment();
        TextSegment.TextData textData = new TextSegment.TextData();
        textData.setText("更像联机平台吧");
        text.setData(textData);
        text.setType("text");
        segments.add(text);

        String rendered = MessageLexer.render(segments, MessageLexer.RenderMode.ENVELOPE);
        assertTrue(rendered.contains("[引用#1512804543]"), "引用记号丢了：" + rendered);
        assertTrue(rendered.contains("更像联机平台吧"), "正文丢了：" + rendered);
    }

    @Test
    @DisplayName("引用段没有 id 时退化成一个光秃秃的 [引用]，不抛异常")
    void replySegmentWithoutIdStillRenders() {
        List<OneBotMessageSegment> segments = new ArrayList<>();
        ReplySegment reply = new ReplySegment();
        reply.setType("reply");
        segments.add(reply);
        assertEquals("[引用]", MessageLexer.render(segments, MessageLexer.RenderMode.ENVELOPE));
    }
}
