package io.github.mangomaner.mangobot.adapter.onebot.model.segment;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 位置消息段。
 *
 * <pre>
 * [CQ:location,lat=39.8969426,lon=116.3109099]
 * {"type":"location","data":{"lat":"...","lon":"...","title":"...","content":"..."}}
 * </pre>
 *
 * <p>{@code lat}/{@code lon} 在 NapCat 的 schema 里是 {@code string | number} 二选一，这里统一用
 * {@link String} 收：Jackson 的字符串反序列化器遇到数字会读成文本（{@code 39.89} → {@code "39.89"}），
 * 所以入站两种形态都接得住；出站也照 OneBot 文档的例子发字符串。用 double 反而会在
 * "数字精度"和"空值"上多出无谓的分支。
 *
 * <p>{@code title}/{@code content} 是发送时可选、接收时不一定有的字段，两个方向都允许为空。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class LocationSegment extends OneBotMessageSegment {

    private LocationData data;

    @Data
    public static class LocationData {
        /** 纬度（必填） */
        private String lat;

        /** 经度（必填） */
        private String lon;

        /** 发送时可选，标题 */
        private String title;

        /** 发送时可选，内容描述 */
        private String content;
    }
}
