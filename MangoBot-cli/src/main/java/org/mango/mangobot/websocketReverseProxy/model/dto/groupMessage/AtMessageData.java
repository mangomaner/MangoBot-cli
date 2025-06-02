package org.mango.mangobot.websocketReverseProxy.model.dto.groupMessage;

import lombok.Data;

/**
 * @某人消息内容
 */
@Data
public class AtMessageData extends MessageSegment {
    public AtMessageData() {
        setType("at");
    }

    private Data data = new Data();

    @lombok.Data
    public class Data {
        /**
         * QQ号，填 "all" 表示@全体成员
         */
        private String qq;
    }
}