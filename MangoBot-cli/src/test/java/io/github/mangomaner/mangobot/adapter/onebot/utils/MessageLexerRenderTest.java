package io.github.mangomaner.mangobot.adapter.onebot.utils;

import io.github.mangomaner.mangobot.adapter.onebot.model.segment.JsonSegment;
import io.github.mangomaner.mangobot.adapter.onebot.model.segment.LocationSegment;
import io.github.mangomaner.mangobot.adapter.onebot.model.segment.OneBotMessageSegment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link MessageLexer#render} 对各类消息段的文本化：位置与卡片。
 *
 * <p>这些是"这条消息在模型眼里长什么样"的契约。钉它们是因为<b>渲染错了不会报错，只会让模型读不懂</b>：
 * 位置曾经落到 default 分支渲染成一个光秃秃的 {@code [json]} / {@code [location]}，
 * 模型看到的就是"有人发了个看不懂的东西"。
 *
 * <p>同包下 {@link EnvelopeMarkersTest} 管的是信封上那两个记号（{@code [消息#id]} / {@code [引用#id]}），
 * 与这里的分工是"记号"对"正文渲染"——别把两件事混进一个文件。
 */
class MessageLexerRenderTest {

    // —————————— 位置段 (location) ——————————

    @Test
    @DisplayName("位置段带上标题与坐标，而不是兜底的 [location]")
    void locationSegmentRendersCoords() {
        assertEquals("[位置: 北京市海淀区 中关村大街 (39.8969426,116.3109099)]",
                render(location("39.8969426", "116.3109099", "北京市海淀区", "中关村大街")));
    }

    @Test
    @DisplayName("位置段缺 title/content 时只留坐标，不留空冒号")
    void locationSegmentRendersWithoutLabel() {
        assertEquals("[位置 (39.8969426,116.3109099)]",
                render(location("39.8969426", "116.3109099", null, "  ")));
    }

    @Test
    @DisplayName("位置段 data 为空时退化成 [位置]，不抛异常")
    void locationSegmentWithoutDataStillRenders() {
        LocationSegment segment = new LocationSegment();
        segment.setType("location");
        assertEquals("[位置]", render(segment));
    }

    // —————————— 卡片 (json) ——————————

    /**
     * 真人发的位置在 NapCat 里是 {@code json} 段的腾讯地图 Ark，不是 {@code location} 段。
     *
     * <p>这条 JSON 是从本机群聊记录里原样抄出来的（只截了与渲染有关的字段）。不认它的话，
     * 位置在信封里完全不存在——模型只知道"有人发了点什么"，不知道在哪。
     */
    @Test
    @DisplayName("腾讯地图 Ark 渲染成 [位置: 名称 地址 (纬度,经度)]，不再是一个 [json]")
    void arkLocationRendersAsLocation() {
        String ark = """
                {"app":"com.tencent.map","desc":"","from":1,"prompt":"[位置]徐汇区徐家汇地铁站东(漕溪北路东)",
                 "ver":"1.1.2.21","view":"LocationShare","meta":{"Location.Search":{
                 "address":"上海市徐汇区漕溪北路33号","enum_relation_type":1,"from":"plusPanel",
                 "from_account":1536958522,"id":"","lat":"31.191692","lng":"121.438489",
                 "name":"徐汇区徐家汇地铁站东(漕溪北路东)","uint64_peer_account":752930707}}}""";

        assertEquals("[位置: 徐汇区徐家汇地铁站东(漕溪北路东) 上海市徐汇区漕溪北路33号 (31.191692,121.438489)]",
                render(json(ark)));
    }

    @Test
    @DisplayName("认不出的卡片退化成 prompt，再不行才是 [卡片]")
    void otherCardsFallBackToPrompt() {
        assertEquals("[位置]体育西路", render(json("{\"prompt\":\"[位置]体育西路\"}")));
        assertEquals("[卡片]", render(json("{\"foo\":\"bar\"}")));
    }

    @Test
    @DisplayName("卡片不是合法 JSON、或缺 data 时不抛异常")
    void malformedCardStillRenders() {
        assertEquals("[卡片]", render(json("not json at all")));
        JsonSegment empty = new JsonSegment();
        empty.setType("json");
        assertEquals("[卡片]", render(empty));
    }

    // —————————— 畸形段 ——————————

    @Test
    @DisplayName("段没有 type 时渲染 [未知]，不是 [null]")
    void segmentWithoutTypeRendersUnknown() {
        assertEquals("[未知]", render(new JsonSegment()));
    }

    // —————————— 构造 ——————————

    private static String render(OneBotMessageSegment segment) {
        return MessageLexer.render(List.of(segment), MessageLexer.RenderMode.ENVELOPE);
    }

    private static JsonSegment json(String data) {
        JsonSegment segment = new JsonSegment();
        JsonSegment.JsonData jsonData = new JsonSegment.JsonData();
        jsonData.setData(data);
        segment.setData(jsonData);
        segment.setType("json");
        return segment;
    }

    private static LocationSegment location(String lat, String lon, String title, String content) {
        LocationSegment segment = new LocationSegment();
        LocationSegment.LocationData data = new LocationSegment.LocationData();
        data.setLat(lat);
        data.setLon(lon);
        data.setTitle(title);
        data.setContent(content);
        segment.setData(data);
        segment.setType("location");   // type 由 Jackson 反序列化时填；手工构造要自己设
        return segment;
    }
}
