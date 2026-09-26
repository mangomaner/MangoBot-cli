package io.github.mangomaner.mangobot.adapter.onebot.model.segment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 位置段的持久化往返。
 *
 * <p><b>{@code location} 段只用于接收，发送侧没有这条路</b>——发位置走的是腾讯地图 Ark 卡片
 * （见 {@code LocationCard}）。所以这里测的不是"发出去"，而是落库那一条链路：
 * {@code MessageStoreHandler} 把入站事件的段列表 {@code writeValueAsString} 进
 * {@code message_segments} 列，读回来时再按 {@code List<OneBotMessageSegment>} 反序列化。
 *
 * <p>为什么仍值得钉：{@code @JsonTypeInfo} 用的是 {@code As.EXISTING_PROPERTY}，{@code type}
 * 取自对象自己的字段、子类型表里也得有登记，两个条件缺一个都会静默坏掉——存进去的 JSON 少了
 * {@code "type":"location"}，读回来就还原不出 {@code LocationSegment}，而这两步之间隔着一个数据库，
 * 坏了不会有任何报错。
 */
class LocationSegmentSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("落库成 {\"type\":\"location\",\"data\":{...}}，type 不能丢")
    void serializesWithTypeProperty() throws Exception {
        LocationSegment segment = new LocationSegment();
        LocationSegment.LocationData data = new LocationSegment.LocationData();
        data.setLat("39.8969426");
        data.setLon("116.3109099");
        data.setTitle("北京市海淀区");
        data.setContent("中关村大街");
        segment.setData(data);
        segment.setType("location");

        String json = objectMapper.writeValueAsString(segment);

        assertTrue(json.contains("\"type\":\"location\""), "出站 JSON 缺 type=location：" + json);
        assertTrue(json.contains("\"lat\":\"39.8969426\""), "出站 JSON 缺 lat：" + json);
        assertTrue(json.contains("\"lon\":\"116.3109099\""), "出站 JSON 缺 lon：" + json);
        assertTrue(json.contains("\"title\":\"北京市海淀区\""), "出站 JSON 缺 title：" + json);
    }

    /**
     * 存进去再读回来，坐标原样还原。
     *
     * <p>只测序列化的话，子类型表里漏登记要等到"读回来"那一步才暴露；而生产上那一步在另一个
     * 请求里发生，中间隔着数据库，坏了很难联想到是这里。
     */
    @Test
    @DisplayName("落库再读回，坐标原样还原")
    void roundTripsThroughTheSubtypeTable() throws Exception {
        LocationSegment segment = new LocationSegment();
        LocationSegment.LocationData data = new LocationSegment.LocationData();
        data.setLat("39.8969426");
        data.setLon("116.3109099");
        segment.setData(data);
        segment.setType("location");

        String json = objectMapper.writeValueAsString(List.of(segment));

        CollectionType listType = objectMapper.getTypeFactory()
                .constructCollectionType(List.class, OneBotMessageSegment.class);
        List<OneBotMessageSegment> parsed = objectMapper.readValue(json, listType);

        assertEquals(1, parsed.size());
        assertTrue(parsed.get(0) instanceof LocationSegment, "读回来不是 LocationSegment：" + parsed.get(0));
        LocationSegment.LocationData parsedData = ((LocationSegment) parsed.get(0)).getData();
        assertEquals("39.8969426", parsedData.getLat());
        assertEquals("116.3109099", parsedData.getLon());
    }
}
