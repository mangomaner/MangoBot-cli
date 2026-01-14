package io.github.mangomaner.mangobot.model.onebot.segment;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 位置消息段
 * [CQ:location,lat=39.8969426,lon=116.3109099]
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class LocationSegment extends MessageSegment {
    private LocationData data;

    @Data
    public static class LocationData {
        /**
         * 纬度
         */
        private String lat;
        
        /**
         * 经度
         */
        private String lon;
        
        /**
         * 发送时可选，标题
         */
        private String title;
        
        /**
         * 发送时可选，内容描述
         */
        private String content;
    }
}
