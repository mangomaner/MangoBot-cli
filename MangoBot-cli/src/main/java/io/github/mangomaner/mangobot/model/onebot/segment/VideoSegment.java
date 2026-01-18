package io.github.mangomaner.mangobot.model.onebot.segment;

import com.fasterxml.jackson.annotation.JsonProperty;
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
        @JsonProperty("file_size")
        private String fileSize;
    }
}
