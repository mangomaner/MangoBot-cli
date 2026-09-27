package io.github.mangomaner.mangobot.module.agent.context;

import io.github.mangomaner.mangobot.infra.claude.transcript.ClaudeContextUsage;
import io.github.mangomaner.mangobot.infra.claude.transcript.ClaudeTranscript;
import io.github.mangomaner.mangobot.module.agent.model.enums.SessionSource;
import io.github.mangomaner.mangobot.module.agent.runtime.claude.ClaudeRuntimeManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 上下文用量，"查看上下文"页面的数据源。
 *
 * <p>两种口径：转录里每次调用的 usage 自己算（不花钱），{@code /context} 问 CLI 要分类明细
 * （会污染转录，所以跑在副本上）。两边的机制都在 infra，这里只做业务侧的取用与转述。
 */
@Service
@Slf4j
public class ContextAnalysisService {

    private final ClaudeRuntimeManager runtimeManager;

    public ContextAnalysisService(ClaudeRuntimeManager runtimeManager) {
        this.runtimeManager = runtimeManager;
    }

    /** 逐次的上下文大小，从转录里的 usage 算出来。 */
    public List<ClaudeContextUsage.CallUsage> usageByCall(String sessionKey, String sessionId) {
        ClaudeTranscript.Transcript transcript = runtimeManager.transcript().load(sessionKey, sessionId);
        return transcript == null ? List.of() : ClaudeContextUsage.byCall(transcript.entries());
    }

    /**
     * 这个会话最近一次模型调用的上下文大小；读不到返回 0。
     *
     * <p>给"该不该压缩"的判断用——和页面上显示的是同一个数。
     */
    public long latestContextTokens(String sessionKey, String sessionId) {
        return ClaudeContextUsage.latestTotal(runtimeManager.transcript().load(sessionKey, sessionId));
    }

    /** 转录里发生过的压缩。 */
    public List<ClaudeContextUsage.Compaction> compactionsOf(String sessionKey, String sessionId) {
        ClaudeTranscript.Transcript transcript = runtimeManager.transcript().load(sessionKey, sessionId);
        return transcript == null ? List.of() : ClaudeContextUsage.compactions(transcript.entries());
    }

    /** 让 CLI 给出上下文分类明细；失败时返回一句可读的原因。 */
    public String reportFromCli(String sessionKey, String botId, SessionSource source) {
        return runtimeManager.contextReport(sessionKey, botId, source);
    }
}
