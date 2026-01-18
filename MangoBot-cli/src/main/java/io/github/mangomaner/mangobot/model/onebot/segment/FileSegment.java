package io.github.mangomaner.mangobot.model.onebot.segment;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class FileSegment extends MessageSegment {
    private FileData data;

    @Data
    public static class FileData {
        private String file;
        private String url;
        @JsonProperty("file_id")
        private String fileId;
        private String path;
        @JsonProperty("file_size")
        private String fileSize;
    }
}
