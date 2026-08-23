package io.github.mangomaner.mangobot.api;

import io.agentscope.core.message.ContentBlock;
import io.github.mangomaner.mangobot.module.agent.chat.ChatOrchestrator;
import io.github.mangomaner.mangobot.module.agent.model.enums.SessionSource;
import io.github.mangomaner.mangobot.module.agent.model.vo.ChatSessionVO;
import io.github.mangomaner.mangobot.module.agent.service.ChatSessionService;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * Agent API（静态工具类）
 *
 * <p>提供会话管理与对话能力。v2 升级后 Agent 是无状态单例（每个 Bot 一个 HarnessAgent），
 * 不再由调用方创建/持有 Agent 实例：
 * <ul>
 *   <li>{@link #streamChat(Integer, String)} 内部按 sessionId 解析 Bot 单例并组装 RuntimeContext；</li>
 *   <li>createAgent / createAgentWithPrompt 已删除，per-call systemPrompt 由 workspace/AGENTS.md 承载。</li>
 * </ul>
 */
public class MangoAgentApi {

    private static ChatSessionService chatSessionService;
    private static ChatOrchestrator chatOrchestrator;

    private MangoAgentApi() {
    }

    static void setChatSessionService(ChatSessionService service) {
        MangoAgentApi.chatSessionService = service;
    }

    static void setChatOrchestrator(ChatOrchestrator orchestrator) {
        MangoAgentApi.chatOrchestrator = orchestrator;
    }

    private static void checkServices() {
        if (chatSessionService == null || chatOrchestrator == null) {
            throw new IllegalStateException("MangoAgentApi has not been initialized yet.");
        }
    }

    // ==================== 会话管理 ====================

    /**
     * 根据 botId 和 chatId 获取会话详情（不存在时自动创建）
     *
     * @param botId  Bot ID
     * @param chatId 群聊ID/私聊ID
     * @param source 会话来源
     * @return 会话视图对象
     */
    public static ChatSessionVO getSessionByBotIdAndChatId(String botId, String chatId, SessionSource source) {
        checkServices();
        return chatSessionService.getSessionByBotIdAndChatId(botId, chatId, source);
    }

    /**
     * 根据ID获取会话详情
     *
     * @param sessionId 会话ID
     * @return 会话视图对象
     */
    public static ChatSessionVO getSessionById(Integer sessionId) {
        checkServices();
        return chatSessionService.getSessionById(sessionId);
    }

    // ==================== Agent 对话 ====================

    /**
     * 执行流式对话
     * <p>
     * 该方法会：
     * 1. 按 sessionId 解析该会话所属 Bot 的常驻 HarnessAgent（无状态单例）
     * 2. 组装 RuntimeContext（userId/sessionId 均文件系统安全），注入 ChatContext 供工具使用
     * 3. 将会话消息持久化到数据库
     * 4. 通过 streamEvents 流式返回 AI 响应（内部映射为现有 SSE 文本协议）
     * 5. 自动将 AI 回复持久化到数据库
     * 6. 若该会话已有 in-flight 调用，先打断旧调用再发起新一轮（运行中打断 + 合并续答）
     *
     * @param sessionId 对话会话ID
     * @param message   用户消息内容
     * @return 流式响应（Server-Sent Events）
     */
    public static Flux<String> streamChat(Integer sessionId, String message) {
        checkServices();
        return chatOrchestrator.streamChat(sessionId, message);
    }

    /**
     * 执行流式对话（带发送者标识）
     *
     * @param sessionId  对话会话ID
     * @param message    用户消息内容（原始文本，无需 XML 包装）
     * @param senderName 发送者标识（群聊/私聊为发送者 QQ 号）
     * @return 流式响应（Server-Sent Events）
     */
    public static Flux<String> streamChat(Integer sessionId, String message, String senderName) {
        checkServices();
        return chatOrchestrator.streamChat(sessionId, message, senderName);
    }

    /**
     * 执行流式对话（多模态内容块：TextBlock 信封 + ImageBlock 真图）。
     *
     * @param sessionId  对话会话ID
     * @param blocks     用户消息内容块（文本 + 图片）
     * @param senderName 发送者标识（群聊/私聊为发送者 QQ 号）
     * @return 流式响应（Server-Sent Events）
     */
    public static Flux<String> streamChat(Integer sessionId, List<ContentBlock> blocks, String senderName) {
        checkServices();
        return chatOrchestrator.streamChat(sessionId, blocks, senderName);
    }

    /**
     * 中断指定会话正在进行的调用（per-session，不影响其他会话）
     *
     * @param sessionId 会话ID
     */
    public static void interruptChat(Integer sessionId) {
        checkServices();
        chatOrchestrator.interrupt(sessionId);
    }

    /**
     * 判断指定会话当前是否有正在进行的调用
     *
     * @param sessionId 会话ID
     * @return 是否运行中
     */
    public static boolean isChatRunning(Integer sessionId) {
        checkServices();
        return chatOrchestrator.isRunning(sessionId);
    }

    /**
     * 清空指定会话中 bot 的对话记忆（AgentState），下一轮回复从空白上下文开始。
     *
     * @param botId  Bot ID
     * @param source 会话来源
     * @param chatId 群ID/私聊QQ
     */
    public static void clearSessionContext(String botId, SessionSource source, String chatId) {
        checkServices();
        chatOrchestrator.clearSessionContext(botId, source, chatId);
    }
}
