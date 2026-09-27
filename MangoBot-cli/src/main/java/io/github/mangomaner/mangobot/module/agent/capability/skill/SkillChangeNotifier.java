package io.github.mangomaner.mangobot.module.agent.capability.skill;

import io.github.mangomaner.mangobot.module.agent.chat.TurnControl;
import io.github.mangomaner.mangobot.module.agent.chat.TurnControlRegistry;
import io.github.mangomaner.mangobot.module.agent.runtime.claude.ClaudeRuntimeManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.BiPredicate;

/**
 * 技能变更之后统一的通知出口：重建作用域清单 + 让常驻会话重建 + 通过控制模块告知模型。
 *
 * <p>技能页与人格页共用同一套"正文改了该通知谁、范围变了该通知谁、被删了该通知谁"的口径，
 * 两处不会因为各自的写法而走偏。
 *
 * <p><b>只有"技能清单变了"才重建清单表</b>（启用开关、来源、人格指派）；改正文不用——正文就是
 * 进程下次启动时读的那一份，磁盘上没有副本要同步（零副本布局）。
 *
 * <p>为什么必须配控制指令：实测（2026-09-27）技能正文是<b>进程级缓存</b>——改了文件不重建进程，
 * 重读也拿不到新版；而重建之后模型又<b>不会主动重读</b>，会直接从上下文里那份旧的接着答。所以"重建 +
 * 明确要求重读并声明旧版作废"两件缺一不可。温和的"再读一次"实测无效。
 *
 * <p>人格的指派还要再收窄一层：通知对象是"这个作用域下的会话"——全局级是该来源全部会话，
 * bot 级是该 bot 的会话，会话级就那一个。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SkillChangeNotifier {

    private final SkillManager skillManager;
    private final ClaudeRuntimeManager claudeRuntimeManager;
    private final TurnControlRegistry turnControlRegistry;

    /**
     * 技能正文被改写：通知<b>在其来源上能读到它</b>的会话（对人格即"指派里含该来源"的会话）。
     */
    public void contentChanged(String skillPath) {
        broadcast(skillPath, false, reloadControl(skillPath),
                (key, info) -> skillManager.isEnabledFor(skillPath, info.source()));
    }

    /**
     * 启用范围 / 指派变了：清单表要重建，然后让相关会话重判还能不能读到它。
     */
    public void scopeChanged(String skillPath) {
        broadcast(skillPath, true, scopeChangedControl(skillPath),
                (key, info) -> skillManager.isEnabledFor(skillPath, info.source()));
    }

    /**
     * 技能被停用/删除：读过它的会话自己作废。
     *
     * <p>删掉之后 {@code isEnabledFor} 就查不到了，所以这里不再按来源过滤，直接通知所有活会话。
     */
    public void removed(String skillPath) {
        broadcast(skillPath, true, revokedControl(skillPath), (key, info) -> true);
    }

    /**
     * 人格切换：某作用域的人格换了（或被取消），通知该作用域下的会话。
     *
     * <p>场景互斥由 {@link SkillManager#assignPersona(String, PersonaScope)} 保证。
     * 已知取舍：全局级变更也会通知到"已被 bot 级人格遮蔽"的会话，控制文案本身允许这种情况
     * （"如果还能读到就重读"），不值得为它再查一遍清单。
     *
     * @param newPersonaPath 新指派的人格路径；为 null 表示该作用域的人格被取消
     */
    public void personaChanged(PersonaScope scope, String newPersonaPath) {
        String subject = newPersonaPath == null ? "persona:" + scope.key() : newPersonaPath;
        broadcast(subject, true, personaChangedControl(scope, newPersonaPath), (key, info) ->
                scope.level() == PersonaScope.Level.SESSION
                        ? key.equals(scope.key())
                        : info.source() == scope.source()
                                && (scope.level() == PersonaScope.Level.GLOBAL
                                        || scope.botId().equals(info.botId())));
    }

    // ========================================================================
    // 内部
    // ========================================================================

    /**
     * 一次变更的三部曲：重建清单表 → 标 all stale → 给命中的会话排控制指令。
     *
     * <p>{@code markAllSessionsStale()} 与"发给谁"无关，无脑全做——清单与系统提示词都是 spawn 那刻
     * 定死的，已经有会话跑着的时候改配置不重建就永远不生效。控制指令才按 {@code filter} 收窄。
     */
    private void broadcast(String subjectPath, boolean scopesChanged, String controlText,
            BiPredicate<String, ClaudeRuntimeManager.SessionInfo> filter) {
        if (scopesChanged) {
            skillManager.refreshSkillScopes();
        }
        claudeRuntimeManager.markAllSessionsStale();
        if (controlText == null) {
            return;
        }
        // 来源与 bot 由 manager 记着，不从会话键里切——键怎么拼是 infra 与 SessionKeys 的事
        for (Map.Entry<String, ClaudeRuntimeManager.SessionInfo> entry
                : claudeRuntimeManager.runningSessions().entrySet()) {
            if (!filter.test(entry.getKey(), entry.getValue())) {
                continue;
            }
            turnControlRegistry.appendOnce(entry.getKey(),
                    new TurnControl("skill:" + subjectPath, controlText));
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
     *
     * <p>不提名字前缀：{@code --plugin-dir} 指向哪个目录，清单里的名字就带哪个目录名的前缀
     * （零副本布局下每个技能只用自己的名字），写死某个前缀会在换布局时变成错的提示。
     */
    private static String reloadControl(String skillPath) {
        return "技能「" + skillPath + "」的正文已经更新，这是由我方发布并认可的新版本，必须按它执行。"
                + "上文里那份旧版本作废。现在重新调用 Skill 工具读取它（它在清单里的名字可能带插件目录前缀，"
                + "以清单里显示的名字为准）。新版本里与旧版本不一致的地方是有意改的，照新的来，"
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

    /** 人格切换：明确告知"这个作用域的人格已经换成谁"，并背书为新版本指令。 */
    private static String personaChangedControl(PersonaScope scope, String newPersonaPath) {
        if (newPersonaPath == null) {
            return "你所在的场景（" + scope.display() + "）现在没有人格了。"
                    + "请不要再沿用之前的人格设定，按系统提示词里的默认准则行事。";
        }
        return "你所在的场景（" + scope.display() + "）的人格已经切换成「" + newPersonaPath + "」。"
                + "这是由我方发布并认可的新人格，旧人格作废。现在重新调用 Skill 工具读取这个新人格，"
                + "并严格按它来塑造你的行为；如果它和上文任何设定不一致，以它为准。";
    }
}
