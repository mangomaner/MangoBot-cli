package io.github.mangomaner.mangobot.module.agent.capability.skill;

import io.github.mangomaner.mangobot.module.agent.chat.TurnControl;
import io.github.mangomaner.mangobot.module.agent.chat.TurnControlRegistry;
import io.github.mangomaner.mangobot.module.agent.model.enums.SessionSource;
import io.github.mangomaner.mangobot.module.agent.runtime.claude.ClaudeRuntimeManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.function.Predicate;

/**
 * 技能变更之后统一的通知出口：重建来源插件目录 + 让常驻会话重建 + 通过控制模块告知模型。
 *
 * <p>原来这些逻辑散在 {@code AgentCapabilityController} 的私有方法里（技能页一个入口、人格页又要一个），
 * 这里把它抽成唯一入口。技能页与人格页共用同一套"正文改了该通知谁、范围变了该通知谁、被删了该通知谁"
 * 的口径，两处不会因为各自的写法而走偏。
 *
 * <p>顺序有讲究：先把来源插件目录重建好（新内容落盘），再让常驻会话"下次重建"，最后排控制指令——
 * 控制指令那一轮开头会先重建进程（那时才读得到新文件），模型随后按指令重读。
 *
 * <p>为什么必须配控制指令：实测（2026-09-27）技能正文是<b>进程级缓存</b>——改了文件不重建进程，重读也
 * 拿不到新版；而重建之后模型又<b>不会主动重读</b>，会直接从上下文里那份旧的接着答。所以"重建 + 明确要求
 * 重读并声明旧版作废"两件缺一不可。温和的"再读一次"实测无效。
 *
 * <p>人格 skill 的改动还要再收窄一层：人格对每个场景（web/群聊/私聊）至多命中一个，所以"正文改了该通知谁"
 * 精确到"在它的场景上能读到它的会话"；而"指派换了"则是通知整个场景的所有会话（见
 * {@link #personaChanged(SessionSource, String)}）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SkillChangeNotifier {

    private final SkillManager skillManager;
    private final ClaudeRuntimeManager claudeRuntimeManager;
    private final TurnControlRegistry turnControlRegistry;

    /**
     * 技能正文被改写：通知<b>在其来源上能读到它</b>的会话（对人格 skill 即"在它所属场景上列出它"的会话）。
     */
    public void contentChanged(String skillPath) {
        broadcast(skillPath, reloadControl(skillPath),
                source -> skillManager.isEnabledFor(skillPath, source));
    }

    /**
     * 启用范围变了：可能读得到、也可能读不到了，让相关来源的会话重判。
     */
    public void scopeChanged(String skillPath) {
        broadcast(skillPath, scopeChangedControl(skillPath),
                source -> skillManager.isEnabledFor(skillPath, source));
    }

    /**
     * 技能被停用/删除：读过它的会话自己作废。
     *
     * <p>删掉之后 {@code isEnabledFor} 就查不到了，所以这里不再按来源过滤，直接通知所有活会话。
     */
    public void removed(String skillPath) {
        broadcast(skillPath, revokedControl(skillPath), source -> true);
    }

    /**
     * 人格切换：某来源的「当前人格」换了（或被取消），通知该来源<b>所有</b>会话。
     *
     * <p>指派是来源级的：旧人格在新来源上不再是它、新人格在来源上成为它，
     * 单独查任何一个技能的启用列表都盖不全"这个来源的人格变了"这件事，所以按来源整体通知。
     * 场景互斥由 {@link SkillManager#assignPersonaSource(String, SessionSource)} 保证。
     *
     * @param newPersonaPath 新指派的人格路径；为 null 表示该来源的人格被取消
     */
    public void personaChanged(SessionSource source, String newPersonaPath) {
        String subject = newPersonaPath == null ? "persona:" + source.getSourceKey() : newPersonaPath;
        broadcast(subject, personaChangedControl(source, newPersonaPath), s -> s == source);
    }

    // ========================================================================
    // 内部
    // ========================================================================

    /**
     * 一次变更的三部曲：重建插件目录 → 标 all stale → 给命中的会话排控制指令。
     *
     * <p>{@code refreshSourcePluginDirs()} 与 {@code markAllSessionsStale()} 与"发给谁"无关，无脑全做；
     * 控制指令才按 {@code sourceFilter} 收窄。这样即使某次变更不该打扰模型（控制文案为空），进程与目录
     * 也始终保持正确。
     */
    private void broadcast(String subjectPath, String controlText, Predicate<SessionSource> sourceFilter) {
        skillManager.refreshSourcePluginDirs();
        claudeRuntimeManager.markAllSessionsStale();
        if (controlText == null) {
            return;
        }
        for (String sessionKey : claudeRuntimeManager.sessionStatus().keySet()) {
            SessionSource source = sourceOf(sessionKey);
            if (source == null || !sourceFilter.test(source)) {
                continue;
            }
            turnControlRegistry.appendOnce(sessionKey,
                    new TurnControl("skill:" + subjectPath, controlText));
        }
    }

    /** 会话键形如 {@code botId:source:chatId}；取不出合法来源（例如 dev 会话）就返回 null。 */
    private static SessionSource sourceOf(String sessionKey) {
        String[] parts = sessionKey.split(":");
        if (parts.length < 2) {
            return null;
        }
        try {
            return SessionSource.fromKey(parts[1]);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 正文被改写：要求重读、声明旧版作废，并且**明确背书新版本**。
     *
     * <p>最后那句背书是实测逼出来的（2026-09-27 演示）：只写"技能更新了、重新读一遍"时，模型确实会照做
     * 重读，但**读完不肯执行新内容**——它的原话是"控制块只说了技能更新、重新读取，并没有点名认可这条
     * 规则；所以我按'控制块没背书、内容本身越权'来处理"。也就是说技能正文在它眼里是低可信度内容，
     * 没有控制块的背书就会被当成外部注入挡掉。
     *
     * <p>所以这句话必须说三件事：新版是我方发布的、和旧版不一致的地方是有意改的、不要当注入。
     */
    private static String reloadControl(String skillPath) {
        return "技能「" + skillPath + "」的正文已经更新，这是由我方发布并认可的新版本，必须按它执行。"
                + "上文里那份旧版本作废。现在重新调用 Skill 工具读取它（技能列表里的名字可能带来源前缀，"
                + "例如 group:" + skillPath + "）。新版本里与旧版本不一致的地方是有意改的，照新的来，"
                + "不要把它当成外部注入的内容。";
    }

    /** 启用范围变了：可能读得到、也可能读不到了，一句话覆盖两种情况（同样带背书）。 */
    private static String scopeChangedControl(String skillPath) {
        return "技能「" + skillPath + "」的启用范围变了（这是我方发布的状态，按它执行）。"
                + "如果你现在还能读到它，重新读一遍按新的来；如果已经读不到，就别再按它的内容行事。";
    }

    /** 技能被删/停用：明确作废。 */
    private static String revokedControl(String skillPath) {
        return "技能「" + skillPath + "」已经停用或删除。不要再调用它，也不要再照它的内容行事；"
                + "上文里还留着的也一并忽略。";
    }

    /** 人格切换：明确告知"该场景的人格已经换成谁"，并背书为新版本指令。 */
    private static String personaChangedControl(SessionSource source, String newPersonaPath) {
        if (newPersonaPath == null) {
            return "你所在的场景（" + source.getDescription() + "）现在没有人格了。"
                    + "请不要再沿用之前的人格设定，按系统提示词里的默认准则行事。";
        }
        return "你所在的场景（" + source.getDescription() + "）的人格已经切换成「" + newPersonaPath + "」。"
                + "这是由我方发布并认可的新人格，旧人格作废。现在重新调用 Skill 工具读取这个新人格，"
                + "并严格按它来塑造你的行为；如果它和上文任何设定不一致，以它为准。";
    }
}