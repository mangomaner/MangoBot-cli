package io.github.mangomaner.mangobot.adapter.onebot.handler.outbound.build_sending_message;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.mangomaner.mangobot.adapter.onebot.model.segment.JsonSegment;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 发送位置用的腾讯地图 Ark 卡片——<b>发位置只有这一条路</b>。
 *
 * <h3>为什么不是 {@code location} 段</h3>
 * OneBot 协议里有个 {@code {"type":"location","data":{"lat","lon"}}} 段，NapCat 的接口文档也认它，
 * 但<b>实测发出去 QQ 不认</b>：NapCat 4.15.1 把它转成 ElementType=28，自己日志里标「未实现」，
 * 客户端显示「该消息类型暂不支持查看」。而且翻遍本机群聊记录，<b>真人发的位置一条 {@code location} 段都没有</b>
 * ——全是这个 Ark 卡片。所以这里照实测的真身来拼。
 *
 * <h3>字段是抄来的，不是猜的</h3>
 * 下面这份结构逐字段抄自本机群聊记录里一条真实的位置消息（只去掉与渲染无关的统计字段）：
 * <pre>
 * {"app":"com.tencent.map","desc":"","from":1,"prompt":"[位置]徐汇区徐家汇地铁站东(漕溪北路东)",
 *  "ver":"1.1.2.21","view":"LocationShare","meta":{"Location.Search":{
 *    "address":"上海市徐汇区漕溪北路33号","enum_relation_type":1,"from":"plusPanel",
 *    "from_account":1536958522,"id":"","lat":"31.191692","lng":"121.438489",
 *    "name":"徐汇区徐家汇地铁站东(漕溪北路东)","uint64_peer_account":752930707}}}
 * </pre>
 *
 * <p>两个容易踩的点：坐标键是 <b>{@code lng}</b> 不是 {@code lon}（OneBot 段用 lon，这里不一样）；
 * {@code meta} 的键名是带点的 <b>{@code "Location.Search"}</b>，不是嵌套对象。
 */
public final class LocationCard {

    /** {@code name} 为空时的兜底：卡片上总得有个地名才像话。 */
    private static final String DEFAULT_NAME = "我在这";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private LocationCard() {
    }

    /**
     * 拼一张位置卡片的消息段。
     *
     * @param fromAccount 发这张卡片的人（bot 自己）的 QQ 号，写进 {@code Location.Search.from_account}
     * @param peerAccount 收这张卡片的一方（群号 / 好友号），写进 {@code uint64_peer_account}
     * @param lat         纬度
     * @param lng         经度
     * @param name        地点名（如「中关村大街」）；空则用 {@link #DEFAULT_NAME}
     * @param address     详细地址；空则不写
     * @throws IllegalArgumentException lat 或 lng 为空时
     */
    public static JsonSegment toSegment(long fromAccount, long peerAccount, String lat, String lng,
                                        String name, String address) {
        JsonSegment segment = new JsonSegment();
        JsonSegment.JsonData data = new JsonSegment.JsonData();
        data.setData(toJson(fromAccount, peerAccount, lat, lng, name, address));
        segment.setData(data);
        segment.setType("json");
        return segment;
    }

    /** 拼卡片本身的 JSON 文本。抽出来是为了能直接对字符串做断言。 */
    public static String toJson(long fromAccount, long peerAccount, String lat, String lng,
                                String name, String address) {
        if (lat == null || lat.isBlank() || lng == null || lng.isBlank()) {
            throw new IllegalArgumentException("发送位置必须给经纬度：lat=" + lat + " lng=" + lng);
        }
        String cardName = (name == null || name.isBlank()) ? DEFAULT_NAME : name.trim();
        long ctime = System.currentTimeMillis() / 1000;

        ObjectNode search = MAPPER.createObjectNode();
        search.put("address", address == null ? "" : address.trim());
        search.put("enum_relation_type", 1);
        search.put("from", "plusPanel");
        search.put("from_account", fromAccount);
        search.put("id", "");
        search.put("lat", lat.trim());
        search.put("lng", lng.trim());
        search.put("name", cardName);
        search.put("uint64_peer_account", peerAccount);

        ObjectNode meta = MAPPER.createObjectNode();
        // 键名就是带点的字符串，别写成嵌套对象
        meta.set("Location.Search", search);

        ObjectNode config = MAPPER.createObjectNode();
        config.put("autosize", false);
        config.put("ctime", ctime);
        config.put("forward", true);
        // 真实值由客户端算，只作统计、不参与定位；这里按坐标与时间派生一个，保证每次不同
        config.put("token", token(lat + lng + ctime));
        config.put("type", "normal");

        ObjectNode card = MAPPER.createObjectNode();
        card.put("app", "com.tencent.map");
        card.set("config", config);
        card.put("desc", "");
        card.put("from", 1);
        card.set("meta", meta);
        card.put("prompt", "[位置]" + cardName);
        card.put("ver", "1.1.2.21");
        card.put("view", "LocationShare");

        try {
            return MAPPER.writeValueAsString(card);
        } catch (Exception e) {
            throw new IllegalStateException("构造位置卡片失败", e);
        }
    }

    /**
     * 派生一个长得像真实 token 的 32 位十六进制串。
     *
     * <p>真实值是客户端算的、只作统计，不参与定位，所以内容无所谓；但<b>不能静默返回空串</b>——
     * 那样卡片会少一个字段，而少了字段的卡片还能不能渲染是没验证过的。算不出来就该炸，不该发一张
     * 结构不完整的卡片出去。
     */
    private static String token(String seed) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(seed.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // MD5 是 JDK 要求必须提供的算法，正常 JVM 走不到这里
            throw new IllegalStateException("计算位置卡片 token 失败", e);
        }
    }
}
