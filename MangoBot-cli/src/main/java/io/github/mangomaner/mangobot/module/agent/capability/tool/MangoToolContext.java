package io.github.mangomaner.mangobot.module.agent.capability.tool;

/**
 * 当前 MCP 工具调用所属的会话键（ThreadLocal）。
 *
 * <p>一个 MCP server 服务所有会话，工具本身是无状态的反射对象，所以"是谁在调我"只能挂在每次请求上。
 * 链路是：claude 连 MCP 时带上 {@code X-Mango-Session} 头 → transport 的 contextExtractor 把它塞进
 * {@code McpTransportContext} → {@link JavaToolMcpServer} 的 callHandler 在调 Java 方法前 set 到这里。
 *
 * <p>为什么是 ThreadLocal 而不是参数：工具方法是注册链路反射调用的普通对象（无参构造），
 * 拿不到 {@code McpTransportContext}，只能靠这个桥把身份递进去。
 *
 * <p>set / clear 必须成对，且都在 callHandler 里——MCP 的 stateless sync handler 是同步调用，
 * 一次调用占一个线程从头到尾，所以 ThreadLocal 在这里是安全的。
 */
public final class MangoToolContext {

    private static final ThreadLocal<String> SESSION_KEY = new ThreadLocal<>();

    private MangoToolContext() {
    }

    /** 当前调用所属的会话键；不在 MCP 调用线程上时为 null。 */
    public static String get() {
        return SESSION_KEY.get();
    }

    public static void set(String sessionKey) {
        SESSION_KEY.set(sessionKey);
    }

    public static void clear() {
        SESSION_KEY.remove();
    }
}
