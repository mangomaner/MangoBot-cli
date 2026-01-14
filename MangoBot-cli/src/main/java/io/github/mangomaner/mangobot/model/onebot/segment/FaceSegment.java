package io.github.mangomaner.mangobot.model.onebot.segment;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class FaceSegment extends MessageSegment {
    private FaceData data;

    @Data
    public static class FaceData {
        private String id;
        @JsonProperty("sub_type")
        private int subType;
    }
}
