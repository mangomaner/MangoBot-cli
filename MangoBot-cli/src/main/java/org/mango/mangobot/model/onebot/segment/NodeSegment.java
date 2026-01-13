package org.mango.mangobot.model.onebot.segment;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 合并转发节点消息段
 * [CQ:node,id=123456]
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class NodeSegment extends MessageSegment {
    private NodeData data;

    @Data
    public static class NodeData {
        /**
         * 转发的消息 ID
         */
        private String id;
        
        /**
         * 发送者 QQ 号
         */
        private String userId;
        
        /**
         * 发送者昵称
         */
        private String nickname;
        
        /**
         * 消息内容，支持字符串或 MessageSegment 列表
         */
        private Object content; 
    }
}
