package org.mango.mangobot.manager.websocketReverseProxy.model.dto.groupMessage;

import lombok.Data;

/**
 * 回复消息内容
 */
@Data
public class ReplyMessageData extends MessageSegment {
    public ReplyMessageData() {
        setType("reply");
    }

    private Data data = new Data();

    @lombok.Data
    public class Data {
        /**
         * 被回复的消息ID
         */
        private String id;
    }
}