package org.mango.mangobot.model.onebot.segment;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class AtSegment extends MessageSegment {
    private AtData data;

    @Data
    public static class AtData {
        private String qq;
        private String name;
    }
}
