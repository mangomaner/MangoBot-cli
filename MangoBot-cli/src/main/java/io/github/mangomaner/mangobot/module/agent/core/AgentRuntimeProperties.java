package io.github.mangomaner.mangobot.module.agent.core;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Agent 运行时公共配置（所有 Bot 共用）。
 * 上下文压缩阈值已按 Bot + 来源改为 DB 配置（agent.compaction），此处不再保留 YAML 兜底。
 *
 * <pre>
 * mangobot:
 *   agent:
 *     memory:
 *       flush-trigger-minutes: 10  # 长期记忆后台异步抽取的最小间隔（分钟）
 *       flush-trigger-messages: 5  # 距上次抽取至少积累的对话轮数（0 表示仅按时间触发）
 * </pre>
 */
@Data
@Component
@ConfigurationProperties(prefix = "mangobot.agent")
public class AgentRuntimeProperties {

    /** 长期记忆配置 */
    private Memory memory = new Memory();

    @Data
    public static class Memory {
        /** 长期记忆后台异步抽取的最小间隔（分钟），0 表示每轮对话都抽取 */
        private long flushTriggerMinutes = 10;
        /** 距上次抽取至少积累的对话轮数后才触发（0 表示仅按时间触发） */
        private int flushTriggerMessages = 5;
    }
}
