package io.github.mangomaner.mangobot.module.agent.capability.tool;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进行中的对话轮次注册表，按会话键索引 {@link TurnContext}。
 *
 * <p>为什么是静态工具类而不是 Spring bean：调用方一半是 Spring bean（轮次编排），一半是注册链路
 * 反射造出来的工具对象（拿不到任何注入），两边唯一的共同点是这个类加载器里的静态入口。
 */
public final class TurnContextRegistry {

    private static final Map<String, TurnContext> CONTEXTS = new ConcurrentHashMap<>();

    private TurnContextRegistry() {
    }

    /** 轮次开始：登记这个会话的上下文。 */
    public static void begin(String sessionKey, TurnContext context) {
        CONTEXTS.put(sessionKey, context);
    }

    /** 当前会话进行中的轮次；没有则返回 null（模型在轮次之外调工具时就是这种）。 */
    public static TurnContext current(String sessionKey) {
        return sessionKey == null ? null : CONTEXTS.get(sessionKey);
    }

    /** 轮次结束：注销。必须放在 finally 里，否则一次异常会让这个会话永久"忙"。 */
    public static void end(String sessionKey) {
        if (sessionKey != null) {
            CONTEXTS.remove(sessionKey);
        }
    }
}
