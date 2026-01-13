package org.mango.mangobot.model.onebot.segment;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 语音消息段
 * [CQ:record,file=http://baidu.com/1.mp3]
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class RecordSegment extends MessageSegment {
    private RecordData data;

    @Data
    public static class RecordData {
        /**
         * 语音文件名
         * 发送时支持：绝对路径 (file://)、网络 URL (http://)、Base64 (base64://)
         */
        private String file;
        
        /**
         * 发送时可选，默认 0，设置为 1 表示变声
         */
        private String magic; 
        
        /**
         * 语音 URL
         */
        private String url;
        
        /**
         * 只在通过网络 URL 发送时有效，表示是否使用已缓存的文件，默认 1
         */
        private String cache; 
        
        /**
         * 只在通过网络 URL 发送时有效，表示是否通过代理下载文件，默认 1
         */
        private String proxy; 
        
        /**
         * 只在通过网络 URL 发送时有效，单位秒，默认不超时
         */
        private String timeout;
    }
}
