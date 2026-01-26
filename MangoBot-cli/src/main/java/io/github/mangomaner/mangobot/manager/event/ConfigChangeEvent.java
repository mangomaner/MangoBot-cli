package io.github.mangomaner.mangobot.manager.event;

import io.github.mangomaner.mangobot.model.onebot.event.Event;
import lombok.Getter;
import lombok.ToString;

/**
 * 配置变更事件
 */
@Getter
@ToString
public class ConfigChangeEvent implements Event {

    private final String key;
    private final String value;
    private final long time;

    public ConfigChangeEvent(String key, String value) {
        this.key = key;
        this.value = value;
        this.time = System.currentTimeMillis() / 1000;
    }

    @Override
    public long getSelfId() {
        return -1; // 配置变更不属于特定机器人实例
    }

    @Override
    public long getTime() {
        return time;
    }

    @Override
    public String getPostType() {
        return "meta_event"; // 归类为元事件
    }
}
