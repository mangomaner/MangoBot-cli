package io.github.mangomaner.mangobot.module.agent.chat;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 控制指令的登记处：**这是"在会话进行中影响 agent 行为"的唯一入口**。
 *
 * <p>两种：
 * <ul>
 *   <li><b>一次性</b>（{@link #appendOnce}）——下一轮带上，然后就没了。适合"技能更新了，重读一遍"
 *       这种做完就结束的事。</li>
 *   <li><b>常驻</b>（{@link #pin}）——每一轮都带，直到 {@link #unpin}。适合"切换形态"这种持续状态：
 *       只发一次是靠不住的，模型可能在长对话里把它冲淡，所以每轮重申。</li>
 * </ul>
 *
 * <p><b>对外只开"增"这一个口</b>，渲染那一侧只读（见 {@link TurnPromptComposer}）。这样加一种新的
 * 控制方式不用碰消息组装，而组装那边也不必知道有多少种控制——两边不会因为对方新增而变化。
 *
 * <p>次序：先一次性（按登记顺序），后常驻（按 pin 顺序）。一次性的通常更急、更具体，放前面不容易
 * 被后面的长文本淹没。
 *
 * <p><b>状态只在内存里</b>：进程重启就空了。这一版可以接受——重启本身会重建进程、重读技能，而常驻
 * 控制如果需要跨重启保留，正确做法是把它和会话状态一起落到会话工作目录里（与 {@code claude-config}
 * 并列），那是下一步的事。规模上也无需担心：每个聊天键一个很小的对象，数量由聊天数决定。
 */
@Component
@Slf4j
public class TurnControlRegistry {

    /** 会话键 → 一次性控制（先进先出）。 */
    private final Map<String, List<TurnControl>> once = new ConcurrentHashMap<>();

    /** 会话键 → 常驻控制（按 pin 顺序，同 id 覆盖）。 */
    private final Map<String, Map<String, TurnControl>> pinned = new ConcurrentHashMap<>();

    /** 排一条一次性控制，下一轮生效。 */
    public void appendOnce(String sessionKey, TurnControl control) {
        once.computeIfAbsent(sessionKey, k -> new ArrayList<>()).add(control);
        log.info("控制指令已排队：session={} id={} text={}", sessionKey, control.id(), control.text());
    }

    /** 挂一条常驻控制；同 id 覆盖（这样"切换形态"就是换个同 id 的值）。 */
    public void pin(String sessionKey, TurnControl control) {
        pinned.computeIfAbsent(sessionKey, k -> new LinkedHashMap<>()).put(control.id(), control);
        log.info("常驻控制已挂上：session={} id={}", sessionKey, control.id());
    }

    /** 撤掉一条常驻控制，按 id。 */
    public void unpin(String sessionKey, String controlId) {
        Map<String, TurnControl> map = pinned.get(sessionKey);
        if (map != null && map.remove(controlId) != null) {
            log.info("常驻控制已撤下：session={} id={}", sessionKey, controlId);
        }
    }

    /**
     * 这一轮要带上的控制指令——<b>只读</b>，不改状态。
     *
     * <p>之所以不在这里就把一次性控制取走：本轮可能压根提交不出去（会话正忙 / 进程重建失败），
     * 取走了就静默丢了。取走由 {@link #commitOnce} 在提交成功之后做。
     */
    public List<TurnControl> peek(String sessionKey) {
        List<TurnControl> result = new ArrayList<>();
        List<TurnControl> queued = once.get(sessionKey);
        if (queued != null) {
            result.addAll(queued);
        }
        Map<String, TurnControl> standing = pinned.get(sessionKey);
        if (standing != null) {
            result.addAll(standing.values());
        }
        return result;
    }

    /** 这一轮提交成功了，把已带上的一次性控制清掉。常驻的不动。 */
    public void commitOnce(String sessionKey) {
        once.remove(sessionKey);
    }

    /** 会话彻底结束时清干净（含常驻）。 */
    public void clear(String sessionKey) {
        once.remove(sessionKey);
        pinned.remove(sessionKey);
    }
}
