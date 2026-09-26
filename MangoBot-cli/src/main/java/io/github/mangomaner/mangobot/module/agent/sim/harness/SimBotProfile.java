package io.github.mangomaner.mangobot.module.agent.sim.harness;

import io.github.mangomaner.mangobot.api.MangoConfigApi;
import io.github.mangomaner.mangobot.module.agent.chat.ChatTarget;
import io.github.mangomaner.mangobot.module.agent.model.enums.SessionSource;

/**
 * 模拟群的身份与播种：固定一个假 bot 号 + 假群号，让模拟走完整真链路。
 *
 * <p><b>为什么必须用假号</b>：{@code botId} 就是事件的 {@code self_id}，而
 * {@code ConnectionSessionManager} 的 {@code sessionsBySelfId} 按它索引——复用真号会<b>劫持</b>
 * 那个 bot 在 8080 那条真连接的出站，模型的回复会发到真人面前。固定用 {@link #SELF_ID} 就天然隔离。
 * 这条隔离属性是整套模拟敢随便开的前提：<b>真实群在结构上不可达</b>。
 *
 * <p><b>能力（人格/技能/工具）按 source 解析、不看 botId</b>，所以假号拿到的人格、技能、MCP 工具
 * 与真 bot 完全一致——这是"模拟测的是生产行为"的前提。
 *
 * <p><b>为什么要播种白名单</b>：{@code MangoEventPublisher} 默认走白名单模式
 * （{@code group.enable_list=true}、{@code group.whitelist=[]}），一个没有专属配置行的 self_id
 * 会把<b>所有</b>群消息拦掉——模拟就一条也喂不进去。播种是幂等的，重复跑不会累积垃圾行。
 */
public final class SimBotProfile {

    /**
     * 模拟 bot 的号。刻意是个 7 位假号：不碰真号（{@code 3970521445} 等），也不会和成员哈希出的
     * 10 位号撞上（见 {@link #memberQq}）。
     */
    public static final long SELF_ID = 1000001L;

    /** 模拟群号。与真群（{@code 420418118} 等）分在完全不同的段位，方便按群号清理/辨认。 */
    public static final long GROUP_ID = 900000001L;

    private static final String GROUP_WHITELIST = "[\"" + GROUP_ID + "\"]";

    private SimBotProfile() {
    }

    /**
     * 模拟会话键 {@code 1000001:group:900000001}。
     *
     * <p>不复刻 {@code ChatTarget.sessionKey()} 的拼法，直接问它要——三处（claude 会话、MCP 请求头、
     * 等待这一轮结束的信号）必须是同一个字符串，复刻一份迟早漂移。
     */
    public static String sessionKey() {
        return new ChatTarget(SELF_ID, SessionSource.GROUP, GROUP_ID).sessionKey();
    }

    /** 让模拟群在这个假 bot 下不被白名单拦掉。幂等。 */
    public static void seedWhitelist() {
        MangoConfigApi.updateBotConfigValue("group.enable_list", SELF_ID, "true");
        MangoConfigApi.updateBotConfigValue("group.whitelist", SELF_ID, GROUP_WHITELIST);
    }

    /**
     * 某条模拟消息的发送者号。
     *
     * <p>写了 qq 就用它；没写就由昵称<b>确定性</b>哈希出一个号——同一份剧本跑两次必须是同一批号，
     * 否则"同样输入同样输出"这个前提就没了。
     *
     * <p>哈希落在 {@code [10^9, 2×10^9)}：10 位、以 1 开头。与 {@link #SELF_ID}（7 位）不可能相等，
     * 所以不会踩到"bot 自己发的消息"那条 self 判断而被丢掉。昵称为空时退化成 0。
     */
    public static long memberQq(String who, Long qq) {
        if (qq != null) {
            return qq;
        }
        if (who == null || who.isBlank()) {
            return 0L;
        }
        long hash = 0L;
        for (int i = 0; i < who.length(); i++) {
            hash = hash * 31L + who.charAt(i);
        }
        return 1_000_000_000L + Math.floorMod(hash, 1_000_000_000L);
    }

    /** 信封里显示的名字；没写昵称时退回号本身。 */
    public static String memberName(String who, Long qq) {
        return who == null || who.isBlank() ? String.valueOf(memberQq(who, qq)) : who;
    }
}
