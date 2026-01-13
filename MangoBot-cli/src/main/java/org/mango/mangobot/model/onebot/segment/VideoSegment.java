package org.mango.mangobot.model.onebot.segment;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class VideoSegment extends MessageSegment{
    private VideoData data;
    @Data
    public static class VideoData {
        private String file;
        private String url;
        private String path;
        private String file_size;
    }
}
