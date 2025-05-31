package org.mango.mangobot.manager.websocketReverseProxy.model.dto.groupMessage;

import lombok.Data;

/**
 * 图片消息内容
 */
@Data
public class ImageMessageData extends MessageSegment {
    public ImageMessageData() {
        setType("image");
    }

    private Data data = new Data();

    @lombok.Data
    public class Data {
        /**
         * 支持：
         * - 本地路径 file:///path/to/image.jpg
         * - 网络路径 http://example.com/image.png
         * - base64 编码 base64://xxxxx
         */
        private String file;
    }
}