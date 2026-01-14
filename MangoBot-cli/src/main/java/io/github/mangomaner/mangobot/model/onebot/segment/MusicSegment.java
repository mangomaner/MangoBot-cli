package io.github.mangomaner.mangobot.model.onebot.segment;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 音乐分享消息段
 * [CQ:music,type=163,id=28949129]
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class MusicSegment extends MessageSegment {
    private MusicData data;

    @Data
    public static class MusicData {
        /**
         * 类型: qq, 163, xm, custom
         */
        private String type; 
        
        /**
         * 歌曲 ID
         */
        private String id;
        
        /**
         * 点击后跳转目标 URL
         */
        private String url;
        
        /**
         * 音乐 URL
         */
        private String audio;
        
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
