package io.github.mangomaner.mangobot.module.agent.chat;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 聊天编排的策略参数。
 *
 * <p>和 {@code ClaudeRuntimeProperties} 分开：那边是进程与生命周期的参数，这边是"聊起来像不像人"的
 * 策略。前缀也刻意分开——两个类绑同一个前缀既让人看不出配置该写哪，IDE 也会报重复。
 *
 * <pre>
 * mangobot:
 *   chat:
 *     group-debounce-millis: 1000
 *     max-images-per-turn: 3
 *     max-system-prompt-chars: 8000
 * </pre>
 */
@Data
@Component
@ConfigurationProperties(prefix = "mangobot.chat")
public class ChatTurnProperties {

    /**
     * 群聊/私聊攒消息的防抖窗口（毫秒）：窗口内每来一条就把定时器重置，静默这么久才开一轮。
     *
     * <p>比"首条立刻开跑"更像真人（真人不会 300ms 内回话），也天然把连发的几条并成一批。
     */
    private long groupDebounceMillis = 1000;

    /**
     * 一轮最多带几张入站图。
     *
     * <p>图片落在提示词缓存边界之外，每轮都是净增输入（实测一张 1.5KB 小图约 +250 token，随尺寸变），
     * 所以这是唯一的成本闸门。取消息里最后 N 张，越近越相关。
     */
    private int maxImagesPerTurn = 3;

    /** 系统提示词的字符数上限（0 或负数表示不限）；超长截断并打 warn。 */
    private int maxSystemPromptChars = 8000;
}
