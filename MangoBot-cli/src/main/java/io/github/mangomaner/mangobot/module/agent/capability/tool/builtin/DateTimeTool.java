package io.github.mangomaner.mangobot.module.agent.capability.tool.builtin;

import io.github.mangomaner.mangobot.annotation.MangoTool;
import io.github.mangomaner.mangobot.annotation.MangoToolMethod;
import io.github.mangomaner.mangobot.annotation.MangoToolParam;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * 内置的日期时间工具。
 *
 * <p>claude 的会话是 {@code --tools ""} 起的——它自带的工具全关着，所以「现在几点」这类事
 * 只能靠这个工具。默认时区取 JVM 的 {@code user.timezone}。
 *
 * <p>同时是 {@link MangoToolMethod} / {@link MangoToolParam} 的参照实现：工具类上标 {@link MangoTool}
 * 提供进数据库的元数据，要暴露的方法标 {@link MangoToolMethod}，参数用 {@link MangoToolParam} 描述。
 */
@MangoTool(name = "DateTimeTool", description = "日期与时间工具", category = "SYSTEM")
public class DateTimeTool {

    private static final String DEFAULT_PATTERN = "yyyy-MM-dd HH:mm:ss";

    @MangoToolMethod(description = "获取当前日期和时间。需要知道「现在」时调用。")
    public String now(
            @MangoToolParam(name = "pattern", description = "日期格式，如 yyyy-MM-dd；不传用 yyyy-MM-dd HH:mm:ss",
                    required = false) String pattern) {
        String effective = usablePattern(pattern);
        String formatted = DateTimeFormatter.ofPattern(effective, Locale.ROOT)
                .format(Instant.now().atZone(ZoneId.systemDefault()));
        return formatted + "（" + ZoneId.systemDefault() + "）";
    }

    @MangoToolMethod(description = "把毫秒时间戳转成可读的日期时间")
    public String fromEpochMillis(
            @MangoToolParam(name = "epochMillis", description = "毫秒时间戳，如 1735689600000") long epochMillis,
            @MangoToolParam(name = "pattern", description = "日期格式，如 yyyy-MM-dd；不传用 yyyy-MM-dd HH:mm:ss",
                    required = false) String pattern) {
        String effective = usablePattern(pattern);
        String formatted = DateTimeFormatter.ofPattern(effective, Locale.ROOT)
                .format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()));
        return formatted + "（" + ZoneId.systemDefault() + "）";
    }

    private static String usablePattern(String pattern) {
        return pattern == null || pattern.isBlank() ? DEFAULT_PATTERN : pattern.trim();
    }
}
