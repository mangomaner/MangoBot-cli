package io.github.mangomaner.mangobot.module.agent.chat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 压缩阈值的读取。
 *
 * <p>配错了不会报错——读成 0 就永远不压（上下文一直涨），读成别的就一直压（bot 反复失忆），
 * 而日志里看不出来。所以单独钉住。
 *
 * <p>"上下文有多大"那部分不在这里测：它已经改成走 {@code ContextAnalysisService}（从转录里读），
 * 也就是页面显示的那个数，取数口径由 {@code ContextAnalysisServiceTest} 钉。
 */
class ChatTurnRunnerCompactionTest {

    @Test
    @DisplayName("阈值正常解析")
    void parsesThreshold() {
        assertEquals(30, ChatTurnRunner.parseThresholdK("30"));
        assertEquals(30, ChatTurnRunner.parseThresholdK(" 30 "));
        assertEquals(0, ChatTurnRunner.parseThresholdK("0"), "0 表示关闭，要能读出来");
    }

    @Test
    @DisplayName("阈值配错时退回默认，而不是把压缩弄成一直开或一直关")
    void badThresholdFallsBack() {
        assertEquals(30, ChatTurnRunner.parseThresholdK("abc"));
        assertEquals(30, ChatTurnRunner.parseThresholdK(null));
        assertEquals(30, ChatTurnRunner.parseThresholdK(""));
    }
}
