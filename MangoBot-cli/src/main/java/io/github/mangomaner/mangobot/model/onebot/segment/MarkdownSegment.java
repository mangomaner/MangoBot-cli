package io.github.mangomaner.mangobot.model.onebot.segment;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class MarkdownSegment extends MessageSegment {
    private MarkdownData data;

    @Data
    public static class MarkdownData {
        private String content;
    }
}
