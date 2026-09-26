package io.github.mangomaner.mangobot.adapter.onebot.handler.outbound.build_sending_message;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 位置卡片的形状。
 *
 * <p>这些断言全是<b>实测抄来</b>的字段名，不是照协议文档写的——{@code location} 段那份文档是对的，
 * 但 QQ 不认。所以这里两个最容易写错的地方各钉一条：
 * <ul>
 *   <li>坐标键是 {@code lng}，不是 OneBot 段里的 {@code lon}；</li>
 *   <li>{@code meta} 的键名是带点的字符串 {@code "Location.Search"}，不是嵌套对象
 *       ({@code {"Location":{"Search":…}}})——两者在 JSON 里长得像，NapCat 只认前者。</li>
 * </ul>
 */
class LocationCardTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("外层壳字段齐全：app / view / ver / prompt")
    void carriesTheOuterShell() throws Exception {
        JsonNode card = objectMapper.readTree(card("39.8969426", "116.3109099", "中关村大街", "北京市海淀区"));

        assertEquals("com.tencent.map", card.path("app").asText());
        assertEquals("LocationShare", card.path("view").asText());
        assertEquals("1.1.2.21", card.path("ver").asText());
        assertEquals("[位置]中关村大街", card.path("prompt").asText());
        assertEquals("normal", card.path("config").path("type").asText());
    }

    @Test
    @DisplayName("坐标的键是 lng 不是 lon —— 写成 lon 卡片就废了")
    void usesLngNotLon() throws Exception {
        JsonNode search = searchNode(card("39.8969426", "116.3109099", "中关村大街", "北京市海淀区"));

        assertEquals("39.8969426", search.path("lat").asText());
        assertEquals("116.3109099", search.path("lng").asText());
        assertTrue(search.path("lng").isTextual(), "lng 应该是字符串形态");
        assertTrue(search.path("lon").isMissingNode(), "不能出现 lon 这个键");
    }

    @Test
    @DisplayName("meta 的键是带点的字面量 \"Location.Search\"，不是嵌套对象")
    void metaKeyIsDottedLiteral() throws Exception {
        JsonNode card = objectMapper.readTree(card("39.8969426", "116.3109099", "中关村大街", "北京市海淀区"));

        assertTrue(card.path("meta").has("Location.Search"), "meta 下要有 \"Location.Search\" 这个字面键");
        assertTrue(card.path("meta").path("Location").isMissingNode(),
                "\"Location\" 不应该作为独立对象出现——那说明写成了嵌套结构，NapCat 认不出");
    }

    @Test
    @DisplayName("带上发卡片的 bot 号与所在的群/好友号")
    void carriesIdentities() throws Exception {
        String json = LocationCard.toJson(3970521445L, 220264051L, "39.8969426", "116.3109099", "中关村大街", "北京市海淀区");
        JsonNode search = searchNode(json);

        assertEquals(3970521445L, search.path("from_account").asLong());
        assertEquals(220264051L, search.path("uint64_peer_account").asLong());
        assertEquals("plusPanel", search.path("from").asText());
        assertEquals("北京市海淀区", search.path("address").asText());
    }

    @Test
    @DisplayName("没给 name 时兜底成「我在这」，卡片上总得有个地名")
    void blankNameFallsBack() throws Exception {
        JsonNode card = objectMapper.readTree(card("39.8969426", "116.3109099", "  ", null));

        // 钉字面量而不是常量：兜底文案是用户可见的输出，常量改成私有之后这里反而是唯一能发现它被改掉的地方
        assertEquals("[位置]我在这", card.path("prompt").asText());
        assertEquals("", searchNode(card.toString()).path("address").asText());
    }

    @Test
    @DisplayName("经纬度为空时直接抛错，不发一张没有坐标的废卡")
    void rejectsMissingCoordinates() {
        assertThrows(IllegalArgumentException.class,
                () -> LocationCard.toJson(1L, 2L, "", "116.3109099", "x", "y"));
        assertThrows(IllegalArgumentException.class,
                () -> LocationCard.toJson(1L, 2L, "39.8969426", null, "x", "y"));
    }

    @Test
    @DisplayName("段本身是 json 类型，data 里装的是卡片 JSON 文本")
    void wrapsIntoJsonSegment() throws Exception {
        var segment = LocationCard.toSegment(3970521445L, 220264051L, "39.8969426", "116.3109099", "中关村大街", "北京市海淀区");

        assertEquals("json", segment.getType());
        // data.data 是字符串，不是对象——OneBot 的 json 段就是这么套的
        JsonNode inner = objectMapper.readTree(segment.getData().getData());
        assertEquals("com.tencent.map", inner.path("app").asText());
        assertFalse(segment.getData().getData().isBlank());
    }

    private JsonNode searchNode(String json) throws Exception {
        return objectMapper.readTree(json).path("meta").path("Location.Search");
    }

    private String card(String lat, String lng, String name, String address) {
        return LocationCard.toJson(3970521445L, 220264051L, lat, lng, name, address);
    }
}
