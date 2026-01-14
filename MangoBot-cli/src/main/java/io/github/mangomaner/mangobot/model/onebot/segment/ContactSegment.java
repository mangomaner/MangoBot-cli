package io.github.mangomaner.mangobot.model.onebot.segment;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 推荐好友/群消息段
 * [CQ:contact,type=qq,id=10001000]
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ContactSegment extends MessageSegment {
    private ContactData data;

    @Data
    public static class ContactData {
        /**
         * 推荐类型: qq 或 group
         */
        private String type; 
        
        /**
         * 被推荐人的 QQ 号或群号
         */
        private String id;
    }
}
