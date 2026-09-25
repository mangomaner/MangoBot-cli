package io.github.mangomaner.mangobot.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记工具类里的一个方法为「claude 可调用的一个工具」。
 *
 * <p>类是注册单位（{@link MangoTool} 提供进数据库的元数据），方法是暴露单位：一个类可以标多个方法，
 * 每个方法各自成为一个 MCP tool，名字默认 {@code <工具名>_<方法名>}。
 *
 * <pre>
 * &#64;MangoTool(name = "WeatherTool", description = "天气查询")
 * public class WeatherTool {
 *     &#64;MangoToolMethod(description = "查询某城市今天的天气")
 *     public String today(&#64;MangoToolParam(name = "city", description = "城市名") String city) { ... }
 * }
 * </pre>
 *
 * <p>只有标了这个注解的 public 方法会被暴露；没标的方法（含 public 的辅助方法）不会被看见。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface MangoToolMethod {

    /**
     * 对外暴露的工具名。留空时用 {@code <工具名>_<方法名>}。
     *
     * <p>MCP 工具名只允许 {@code [A-Za-z0-9_-]}，且长度不超过 64。
     */
    String name() default "";

    /**
     * 工具描述——模型据此决定什么时候调它，是这块最有价值的字段，别留空。
     */
    String description() default "";
}
