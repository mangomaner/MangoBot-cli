package org.mango.mangobot.model.onebot.segment;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class JsonSegment extends MessageSegment {
    private JsonData data;

    @Data
    public static class JsonData {
        private String data; // The JSON string content
    }
}
