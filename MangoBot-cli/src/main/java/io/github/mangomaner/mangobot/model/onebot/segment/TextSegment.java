package io.github.mangomaner.mangobot.model.onebot.segment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class TextSegment extends MessageSegment {
    private TextData data;

    @Data
    public static class TextData {
        private String text;
    }
    
    // Helper to get text directly
    public String getText() {
        return data != null ? data.getText() : null;
    }
}
