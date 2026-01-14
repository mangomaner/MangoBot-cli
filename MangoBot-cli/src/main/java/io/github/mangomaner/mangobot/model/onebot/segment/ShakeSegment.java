package io.github.mangomaner.mangobot.model.onebot.segment;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 窗口抖动（戳一戳）消息段
 * [CQ:shake]
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ShakeSegment extends MessageSegment {
    private ShakeData data;

    @Data
    public static class ShakeData {
    }
}
