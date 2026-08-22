package io.github.mangomaner.mangobot.infra;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mangomaner.mangobot.annotation.PluginPriority;
import io.github.mangomaner.mangobot.annotation.messageHandler.MangoBotEventListener;
import io.github.mangomaner.mangobot.api.MangoConfigApi;
import io.github.mangomaner.mangobot.adapter.onebot.model.event.OneBotEvent;
import io.github.mangomaner.mangobot.events.onebot.message.OneBotGroupMessageEvent;
import io.github.mangomaner.mangobot.events.onebot.message.OneBotPrivateMessageEvent;
import io.github.mangomaner.mangobot.message_handler.MessageStoreHandler;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class MangoEventPublisher {

    @Resource
    private ApplicationContext applicationContext;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private MessageStoreHandler messageStoreHandler;

    private final Map<Class<?>, List<ListenerMethod>> listenerCache = new ConcurrentHashMap<>();
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 10, 1000, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), new ThreadPoolExecutor.CallerRunsPolicy());

    /**
     * 初始化时自动扫描 Spring 容器中带有 @MangoBotEventListener 注解的 Bean，
     * 并注册其中带有 @MangoBotEventListener 的方法。
     */
    @PostConstruct
    public void init() {
        // 优化：只获取类上带有 @MangoBotEventListener 注解的 Bean，避免全量扫描
        Map<String, Object> beans = applicationContext.getBeansWithAnnotation(MangoBotEventListener.class);
        
        for (Object bean : beans.values()) {
            // 避免处理 Spring 内部代理类导致的问题
            Class<?> clazz = bean.getClass();
            // 如果是被 CGLIB 代理的类，获取原始类
            if (clazz.getName().contains("$$")) {
                clazz = clazz.getSuperclass();
            }

            for (Method method : clazz.getDeclaredMethods()) {
                if (method.isAnnotationPresent(MangoBotEventListener.class)) {
                    registerListener(method, bean);
                }
            }
        }
    }

    /**
     * 多线程发布事件
     * @param event
     */
    public void publish(OneBotEvent event) {
        if (shouldBlockEvent(event)) {
            log.debug("事件被黑白名单拦截: {}", event.getClass().getSimpleName());
            // 名单内的聊天始终记录消息：拦截时仍派发强制记录
            if (shouldForceRecord(event)) {
                executor.execute(() -> forceRecord(event));
            }
            return;
        }

        executor.execute(() -> {
            List<ListenerMethod> listeners = getListenersForEvent(event.getClass());
            for (ListenerMethod listener : listeners) {
                try {
                    boolean result = (boolean) listener.method.invoke(listener.bean, event);
                    if (!result) {
                        log.debug("监听器 {} 返回 false，停止传播。", listener.method.getName());
                        break;
                    }
                } catch (Exception e) {
                    log.error("执行监听器时出错: {}", listener.method.getName(), e);
                }
            }
        });
    }

    /**
     * 判断事件是否应该被拦截
     * @param event 事件对象
     * @return true 表示拦截，false 表示放行
     */
    private boolean shouldBlockEvent(OneBotEvent event) {
        Long botId = event.getSelfId();

        if (event instanceof OneBotGroupMessageEvent groupEvent) {
            return shouldBlockGroupMessage(botId, groupEvent.getGroupId());
        } else if (event instanceof OneBotPrivateMessageEvent) {
            return shouldBlockPrivateMessage(botId, ((OneBotPrivateMessageEvent) event).getUserId());
        }

        return false;
    }

    /**
     * 判断群消息是否应该被拦截（黑白名单逻辑）
     */
    private boolean shouldBlockGroupMessage(Long botId, long groupId) {
        String enableListValue = MangoConfigApi.getBotConfigValue("group.enable_list", botId, "true");
        boolean isWhitelistMode = Boolean.parseBoolean(enableListValue);

        String listValue = MangoConfigApi.getBotConfigValue(
                isWhitelistMode ? "group.whitelist" : "group.blacklist", botId, "[]");

        List<String> groupIdList = parseJsonArray(listValue);
        String groupIdStr = String.valueOf(groupId);
        boolean inList = groupIdList.contains(groupIdStr);

        if (isWhitelistMode) {
            return !inList;
        } else {
            return inList;
        }
    }

    /**
     * 判断私聊消息是否应该被拦截（黑白名单逻辑）
     */
    private boolean shouldBlockPrivateMessage(Long botId, long userId) {
        String enableListValue = MangoConfigApi.getBotConfigValue("private.enable_list", botId, "true");
        boolean isWhitelistMode = Boolean.parseBoolean(enableListValue);

        String listValue = MangoConfigApi.getBotConfigValue(
                isWhitelistMode ? "private.whitelist" : "private.blacklist", botId, "[]");

        List<String> userIdList = parseJsonArray(listValue);
        String userIdStr = String.valueOf(userId);
        boolean inList = userIdList.contains(userIdStr);

        if (isWhitelistMode) {
            return !inList;
        } else {
            return inList;
        }
    }

    /**
     * 判断被黑白名单拦截的事件是否仍需要记录（聊天命中消息记录名单）。
     */
    private boolean shouldForceRecord(OneBotEvent event) {
        if (event instanceof OneBotGroupMessageEvent groupEvent) {
            return isInRecordList("group.record_list", groupEvent.getSelfId(), groupEvent.getGroupId());
        }
        if (event instanceof OneBotPrivateMessageEvent privateEvent) {
            return isInRecordList("private.record_list", privateEvent.getSelfId(), privateEvent.getUserId());
        }
        return false;
    }

    /**
     * 强制记录：被黑白名单拦截但命中消息记录名单的聊天，仍保存消息记录。
     */
    private void forceRecord(OneBotEvent event) {
        if (event instanceof OneBotGroupMessageEvent groupEvent) {
            messageStoreHandler.onGroupMessage(groupEvent);
        } else if (event instanceof OneBotPrivateMessageEvent privateEvent) {
            messageStoreHandler.onPrivateMessage(privateEvent);
        }
    }

    private boolean isInRecordList(String configKey, Long botId, long id) {
        String listValue = MangoConfigApi.getBotConfigValue(configKey, botId, "[]");
        return parseJsonArray(listValue).contains(String.valueOf(id));
    }

    /**
     * 解析 JSON 数组字符串为 List
     */
    private List<String> parseJsonArray(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("解析名单 JSON 失败: {}", json, e);
            return Collections.emptyList();
        }
    }

    /**
     * 注册监听器
     * @param method
     * @param instance
     */
    public void registerListener(Method method, Object instance) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        if (parameterTypes.length != 1) {
            log.warn("使用 @MangoBotEventListener 注解的方法 {} 必须有且仅有一个参数。", method.getName());
            return;
        }

        if (method.getReturnType() != boolean.class && method.getReturnType() != Boolean.class) {
            throw new IllegalStateException("使用 @MangoBotEventListener 注解的方法 " + method.getName() + " 必须返回 boolean/Boolean 类型。");
        }

        Class<?> eventType = parameterTypes[0];
        if (!OneBotEvent.class.isAssignableFrom(eventType)) {
            log.warn("方法 {} 的参数必须是 Event 的子类。", method.getName());
            return;
        }

        int priority = 10;
        if (method.isAnnotationPresent(PluginPriority.class)) {
            priority = method.getAnnotation(PluginPriority.class).value();
        }

        ListenerMethod listenerMethod = new ListenerMethod(instance, method, priority);
        listenerCache.computeIfAbsent(eventType, k -> new ArrayList<>()).add(listenerMethod);
        // 添加新监听器后重新排序
        Collections.sort(listenerCache.get(eventType));

        log.info("已注册监听器: {} 用于事件: {}", method.getName(), eventType.getSimpleName());
    }

    /**
     * 注销指定实例的所有监听器
     * @param instance 监听器实例
     */
    public void unregisterListener(Object instance) {
        if (instance == null) {
            return;
        }

        for (List<ListenerMethod> listeners : listenerCache.values()) {
            listeners.removeIf(listener -> listener.bean == instance);
        }
        log.info("已注销实例 {} 的所有监听器", instance.getClass().getSimpleName());
    }

    /**
     * 注销指定监听器
     * @param method 监听器方法
     * @param instance 监听器实例
     */
    public void unregisterListener(Method method, Object instance) {
        if (method == null || instance == null) {
            return;
        }

        Class<?>[] parameterTypes = method.getParameterTypes();
        if (parameterTypes.length != 1) {
            log.warn("尝试注销的方法 {} 参数数量不正确，无法确定事件类型。", method.getName());
            return;
        }

        Class<?> eventType = parameterTypes[0];
        List<ListenerMethod> listeners = listenerCache.get(eventType);

        if (listeners != null) {
            boolean removed = listeners.removeIf(listener -> listener.bean == instance && listener.method.equals(method));
            if (removed) {
                log.info("已注销监听器: {} 用于事件: {}", method.getName(), eventType.getSimpleName());
            }
        }
    }


    private List<ListenerMethod> getListenersForEvent(Class<?> eventClass) {
        List<ListenerMethod> result = new ArrayList<>();

        for (Map.Entry<Class<?>, List<ListenerMethod>> entry : listenerCache.entrySet()) {
            if (entry.getKey().isAssignableFrom(eventClass)) {
                result.addAll(entry.getValue());
            }
        }

        Collections.sort(result);
        return result;
    }

    /**
     * 打印所有已注册的监听器信息，按事件类型分组，每组内按优先级从小到大排列。
     * 格式示例：EventType -> Listener1 (priority=5) -> Listener2 (priority=10)
     */
    public void printAllListeners() {
        if (listenerCache.isEmpty()) {
            log.info("当前没有注册任何监听器。");
            return;
        }

        // 按事件类型排序（可选，便于阅读）
        List<Class<?>> sortedEventTypes = new ArrayList<>(listenerCache.keySet());
        sortedEventTypes.sort(Comparator.comparing(Class::getSimpleName));

        for (Class<?> eventType : sortedEventTypes) {
            List<ListenerMethod> listeners = listenerCache.get(eventType);
            if (listeners == null || listeners.isEmpty()) continue;

            // 构建格式：EventClass -> method1 (priority=X) -> method2 (priority=Y)
            StringBuilder line = new StringBuilder();
            line.append(eventType.getSimpleName()).append("：O");

            // 监听器已按优先级排序（因 registerListener 中每次 add 后都 sort）
            // 但为保险起见，再排一次（或直接使用）
            listeners.stream()
                    .sorted() // 确保按 priority 升序
                    .forEach(listener -> {
                        String methodName = listener.method.getDeclaringClass().getSimpleName() + "." + listener.method.getName();
                        line.append(" -> ").append(methodName).append(" (priority=").append(listener.priority).append(")");
                    });

            log.info(line.toString());
        }
    }

    private static class ListenerMethod implements Comparable<ListenerMethod> {
        final Object bean;
        final Method method;
        final int priority;

        public ListenerMethod(Object bean, Method method, int priority) {
            this.bean = bean;
            this.method = method;
            this.priority = priority;
            this.method.setAccessible(true);
        }

        @Override
        public int compareTo(ListenerMethod o) {
            return Integer.compare(this.priority, o.priority);
        }
    }
}
