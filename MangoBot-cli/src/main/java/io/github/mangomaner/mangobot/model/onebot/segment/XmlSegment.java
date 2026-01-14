package io.github.mangomaner.mangobot.model.onebot.segment;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * XML 消息段
 * [CQ:xml,data=<?xml ...]
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class XmlSegment extends MessageSegment {
    private XmlData data;

    @Data
    public static class XmlData {
        /**
         * XML 内容
         */
        private String data;
    }
}
