package io.github.mangomaner.mangobot.model.onebot.segment;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ReplySegment extends MessageSegment {
    private ReplyData data;

    @Data
    public static class ReplyData {
        private String id;
    }
}
