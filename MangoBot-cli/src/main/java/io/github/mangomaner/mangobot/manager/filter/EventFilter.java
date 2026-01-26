package io.github.mangomaner.mangobot.manager.filter;

import io.github.mangomaner.mangobot.manager.event.ConfigChangeEvent;
import io.github.mangomaner.mangobot.model.onebot.event.Event;

/**
 * 事件过滤器接口
 */
public interface EventFilter {
    
    /**
     * 判断事件是否允许通过
     * @param event 事件
     * @return true: 允许; false: 拦截
     */
    boolean allow(Event event);

    /**
     * 处理配置变更（用于更新过滤器内部状态）
     * @param event 配置变更事件
     */
    void handleConfigChange(ConfigChangeEvent event);
}
