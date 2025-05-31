package org.mango.mangobot.manager.websocketReverseProxy.model.dto.groupMessage;

import lombok.Data;

/**
 * 文本消息内容
 */
@Data
public class TextMessageData extends MessageSegment {
    /**
     * 构造函数设置消息类型为 text
     */
    public TextMessageData() {
        setType("text");
    }

    /**
     * 文本内容
     */
    private Data data = new Data();

    @lombok.Data
    public class Data {
        /**
         * 实际文本内容
         */
        private String text;
    }
}