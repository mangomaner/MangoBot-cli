package org.mango.mangobot.manager.websocketReverseProxy.model.dto.groupMessage;

import lombok.Data;

/**
 * 语音消息内容
 */
@Data
public class RecordMessageData extends MessageSegment {
    public RecordMessageData() {
        setType("record");
    }

    private Data data = new Data();

    @lombok.Data
    public class Data {
        /**
         * 支持：
         * - 本地路径 file:///path/to/audio.mp3
         * - 网络路径 http://example.com/audio.mp3
         * - base64 编码 base64://xxxxx
         */
        private String file;
    }
}