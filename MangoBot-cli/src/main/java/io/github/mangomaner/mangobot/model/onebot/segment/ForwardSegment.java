package io.github.mangomaner.mangobot.model.onebot.segment;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 合并转发消息段 (接收)
 * [CQ:forward,id=123456]
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ForwardSegment extends MessageSegment {
    private ForwardData data;

    @Data
    public static class ForwardData {
        /**
         * 合并转发 ID，需通过 get_forward_msg API 获取具体内容
         */
        private String id;
    }
}
