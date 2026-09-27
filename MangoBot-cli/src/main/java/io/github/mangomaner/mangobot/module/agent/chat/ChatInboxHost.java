package io.github.mangomaner.mangobot.module.agent.chat;

import java.util.List;

/**
 * {@link ChatInbox} 这台状态机需要从外部拿到的三件事。
 *
 * <p>抽成接口而不是直接依赖 {@link ChatInboxRegistry}：状态机里那些"什么时候开轮、什么时候打断、
 * 预算怎么算"的规则很微妙（比如打断预算是**连续**计数、正常跑完才清零），而它们在真实环境里
 * 要跑满一整轮 claude 才看得出来。有个窄接口就能拿假实现把规则单独钉住。
 */
interface ChatInboxHost {

    /** 排一次防抖到期检查。 */
    void scheduleDebounce(ChatInbox inbox, long generation);

    /** 把这一批交给轮次池。 */
    void startTurn(ChatInbox inbox, List<IncomingLine> batch);

    /** 打断这个聊天正在跑的那一轮。 */
    void interruptTurn(ChatInbox inbox);
}
