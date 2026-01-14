package io.github.mangomaner.mangobot.model.onebot.segment;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 戳一戳消息段
 * [CQ:poke,type=126,id=2003]
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class PokeSegment extends MessageSegment {
    private PokeData data;

    @Data
    public static class PokeData {
        /**
         * 类型，见 Mirai 的 PokeMessage 类
         */
        private String type;
        
        /**
         * ID，见 Mirai 的 PokeMessage 类
         */
        private String id;
        
        /**
         * 表情名，见 Mirai 的 PokeMessage 类
         */
        private String name;
    }
}
