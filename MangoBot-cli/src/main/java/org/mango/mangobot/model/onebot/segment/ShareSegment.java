package org.mango.mangobot.model.onebot.segment;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 链接分享消息段
 * [CQ:share,url=http://baidu.com,title=百度]
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ShareSegment extends MessageSegment {
    private ShareData data;

    @Data
    public static class ShareData {
        /**
         * URL
         */
        private String url;
        
        /**
         * 标题
         */
        private String title;
        
        /**
         * 发送时可选，内容描述
         */
        private String content;
        
        /**
         * 发送时可选，图片 URL
         */
        private String image;
    }
}
