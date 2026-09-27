package io.github.mangomaner.mangobot.infra.claude;

import io.github.mangomaner.mangobot.infra.claude.process.ClaudeBinaryResolver;
import io.github.mangomaner.mangobot.infra.claude.report.ClaudeContextReport;
import io.github.mangomaner.mangobot.infra.claude.session.ClaudeSession;
import io.github.mangomaner.mangobot.infra.claude.spec.ClaudeSessionSpec;
import io.github.mangomaner.mangobot.infra.claude.transcript.ClaudeTranscript;
import io.github.mangomaner.mangobot.infra.claude.workspace.ClaudeWorkspace;
import io.github.mangomaner.mangobot.utils.FileUtils;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * claude 会话运行时：持有常驻进程、拉起、回收、关闭。
 *
 * <p><b>它不认识任何业务概念</b>——模型角色、技能、来源、聊天都在调用方那边，这里只见
 * {@link ClaudeSessionSpec}（这次会话被配置成什么样）和 {@link ClaudeWorkspace}（路径怎么摆）。
 * 会话键也由调用方给，本类不解析。
 *
 * <p>进程默认懒启动（第一轮对话时才拉起），常驻会话由巡检线程按空闲时长与数量上限回收。
 * 遵循项目约定：业务初始化不用 {@code @PostConstruct}，由 {@code MangoBotApplication.main()}
 * 显式调用 {@link #init()}；清理走 {@link PreDestroy}。
 */
@Slf4j
@Component
public class ClaudeRuntime {

    private static final String DEFAULT_WORK_DIR = "data/claude-work";
    private static final long PROBE_SETTLE_MILLIS = 2000L;
    private static final long REAP_INTERVAL_MINUTES = 5L;

    /** 一次性会话（探针 / judge）的池名；它们不进会话表，所以永远轮不到淘汰。 */
    private static final String EPHEMERAL_POOL = "__ephemeral__";

    private final ClaudeRuntimeProperties properties;

    // 工作区与转录读取器只看配置、不看二进制，所以构造时就能备好
    private final ClaudeWorkspace workspace;
    private final ClaudeTranscript transcript;

    // 常驻会话；一次性会话（judge / 探针）刻意不进这张表，见 newEphemeral
    private final Map<String, ClaudeSession> sessions = new ConcurrentHashMap<>();

    private volatile Path binary;

    // 初始化阶段判定的不可用原因（被禁用 / 找不到二进制）；这一层没问题时为空串
    private volatile String binaryProblem = "";

    // 空闲回收巡检线程；sessionIdleMinutes 与 maxSessions 都为 0 时不予创建
    private volatile ScheduledExecutorService reaper;

    public ClaudeRuntime(ClaudeRuntimeProperties properties) {
        this.properties = properties;
        this.workspace = ClaudeWorkspace.at(properties.getWorkingDir(), DEFAULT_WORK_DIR);
        this.transcript = new ClaudeTranscript(workspace);
    }

    /**
     * 解析二进制、起巡检线程。由 {@code MangoBotApplication.main()} 显式调用。
     *
     * <p>刻意不在这里探端点、不在这里探针：端点属于调用方的事，探针需要一份 spec。
     */
    public void init() {
        if (!properties.isEnabled()) {
            binaryProblem = "mangobot.claude.enabled=false";
            log.info("Claude 运行时未启用（mangobot.claude.enabled=false）");
            return;
        }

        FileUtils.createDirectory(workspace.root());

        Optional<Path> resolved = ClaudeBinaryResolver.resolve(properties);
        if (resolved.isEmpty()) {
            binaryProblem = "未找到 claude 可执行文件";
            log.error("未找到 claude 可执行文件，运行时不可用。已依次尝试：mangobot.claude.binary-path 配置 → "
                            + "环境变量 {} → {} → {} → PATH 中的 {}。修法二选一：把可执行文件保存为 {}；"
                            + "或已经装过 claude 的话，把 binary-path 指向它现有的文件。",
                    ClaudeBinaryResolver.ENV_OVERRIDE, ClaudeBinaryResolver.distPath(),
                    ClaudeBinaryResolver.userHomePath(), ClaudeBinaryResolver.binaryName(),
                    ClaudeBinaryResolver.distPath());
            return;
        }

        this.binary = resolved.get();
        binaryProblem = "";
        log.info("Claude 运行时二进制就绪：binary={} platform={} workingDir={}（每个会话的 claude 家目录在 <会话目录>/{}}）",
                binary, ClaudeBinaryResolver.platformKey(), workspace.root(),
                ClaudeWorkspace.CLAUDE_CONFIG_SUBDIR);

        startReaper();
    }

    // 二进制这一层是否可用；端点那一层由调用方自己判
    public boolean ready() {
        return binary != null && binaryProblem.isEmpty();
    }

    // 二进制这一层的不可用原因；正常时为空串
    public String problem() {
        return binaryProblem;
    }

    public ClaudeWorkspace workspace() {
        return workspace;
    }

    /** 读转录的入口；页面与用量分析都用它，别再自己去拼路径。 */
    public ClaudeTranscript transcript() {
        return transcript;
    }

    public Path binaryPath() {
        return binary;
    }

    /**
     * 取（或创建）一个常驻会话。
     *
     * <p>{@code spec} 用 {@code Supplier} 而不是实体值：技能目录、模型端点、工具面都会变，
     * 每次拉起进程都要现取最新值。{@code pool} 只在常驻上限的账上用到。
     *
     * @throws IllegalStateException 二进制这一层不可用
     */
    public ClaudeSession getOrCreate(String key, String pool, Supplier<ClaudeSessionSpec> spec) {
        if (!ready()) {
            throw new IllegalStateException("Claude 运行时未就绪：" + problem());
        }
        return sessions.computeIfAbsent(key,
                k -> new ClaudeSession(k, pool, binary, workspace, properties, spec));
    }

    /**
     * 造一个<b>不进会话表</b>的一次性会话；用完必须自己 {@link ClaudeSession#close()}。
     *
     * <p>给开机探针和模拟台的 judge 用：两者都不该占常驻会话的名额，也不该被空闲回收盯上。
     */
    public ClaudeSession newEphemeral(String key, Supplier<ClaudeSessionSpec> spec) {
        if (!ready()) {
            throw new IllegalStateException("Claude 运行时未就绪：" + problem());
        }
        return new ClaudeSession(key, EPHEMERAL_POOL, binary, workspace, properties, spec);
    }

    /** 只影响该池的会话：标记下次重建。 */
    public void refreshPool(String pool) {
        for (ClaudeSession session : sessions.values()) {
            if (pool.equals(session.pool())) {
                session.markStale();
            }
        }
    }

    /**
     * 把单个会话标成"下次拉起时重建"。
     *
     * <p>不丢历史：新进程带 {@code -c} 会把这场对话续下去，只是换上新参数。
     * 要连历史一起丢，用 {@link #resetSession(String)}。
     */
    public void markStale(String key) {
        ClaudeSession session = sessions.get(key);
        if (session != null) {
            session.markStale();
        }
    }

    // 把全部常驻会话标成"下次拉起时重建"；技能 / 人格 / 工具面变更后调
    public void markAllStale() {
        for (ClaudeSession session : sessions.values()) {
            session.markStale();
        }
    }

    // 打断某个会话在跑的那一轮；会话不存在或没有在途轮次时什么都不做
    public void interruptTurn(String key) {
        ClaudeSession session = sessions.get(key);
        if (session != null) {
            session.interrupt();
        }
    }

    /**
     * 清空某个会话的上下文：关掉进程，并删掉它自己的工作区目录（转录就在里面）。
     *
     * <p>两件事都要做：<b>只关进程不够</b>——重新拉起时带 {@code -c}，claude 会把磁盘上那份转录
     * 又续回来，用户会看到"点了清空但没生效"。
     *
     * @return 是否真的动了什么东西（进程或目录）；都没有也算成功——状态已经是"没有上下文"了
     */
    public boolean resetSession(String key) {
        ClaudeSession session = sessions.remove(key);
        if (session != null) {
            session.close();
        }
        boolean deleted = workspace.deleteConfigDir(key);
        log.info("清空上下文：session={} 关掉在跑的进程={} 删掉转录目录={}", key, session != null, deleted);
        return session != null || deleted;
    }

    // 预留在跑会话的诊断信息
    public Map<String, Boolean> sessionStatus() {
        Map<String, Boolean> status = new ConcurrentHashMap<>();
        sessions.forEach((key, session) -> status.put(key, session.isRunning()));
        return status;
    }

    /**
     * 关闭并移除全部常驻会话，返回实际关掉的数量。
     *
     * <p><b>调用方负责换线程</b>：{@code MangoEventPublisher} 实际只跑到 corePoolSize = 1，
     * 全 app 的 OneBot 事件都在那一个线程上派发，而 {@code close()} 最长要等
     * {@code shutdownGraceMillis}，堵在这里会把消息处理一起拖住。
     */
    public int closeAll() {
        int closed = 0;
        for (String key : List.copyOf(sessions.keySet())) {
            ClaudeSession session = sessions.remove(key);
            if (session == null) {
                continue;
            }
            closeQuietly(key, session);
            closed++;
        }
        return closed;
    }

    /**
     * 跑 {@code /context} 取上下文分类明细；失败时返回一句可读的原因。
     *
     * <p>跑在 config 目录的<b>副本</b>上：CLI 会把这条命令连同输出写进转录，对活跃会话跑就是污染。
     */
    public String contextReport(String key, Supplier<ClaudeSessionSpec> spec) {
        return ClaudeContextReport.run(binary, workspace, key, spec);
    }

    /**
     * 开机探针：拉一个进程起来，等一小会儿再关掉，把"二进制加载不了 / 缺 DLL / 起来就崩"这类问题
     * 在启动时暴露，而不是等到第一条群消息。
     *
     * <p>它不是预热——会话按 key 分进程，探针那个进程帮不到任何真实会话。
     *
     * @param spec 探针进程用什么配置；要给一份真实形状的，否则探针证明不了什么
     */
    public void probe(String key, Supplier<ClaudeSessionSpec> spec) {
        ClaudeSession probe = null;
        try {
            probe = newEphemeral(key, spec);
            probe.warmup();
            Thread.sleep(PROBE_SETTLE_MILLIS);
            if (probe.isRunning()) {
                log.info("Claude 运行时探针：进程存活 {}ms 无异常退出", PROBE_SETTLE_MILLIS);
            } else {
                log.error("Claude 运行时探针：进程起来就退了，首轮真实对话必然失败。stderr 尾部：{}",
                        String.join(" | ", probe.stderrTail()));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("Claude 运行时探针失败——首轮真实对话很可能同样失败", e);
        } finally {
            if (probe != null) {
                probe.close();
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        ScheduledExecutorService timer = reaper;
        if (timer != null) {
            timer.shutdownNow();
        }
        log.info("Shutting down {} claude session(s)", sessions.size());
        closeAll();
    }

    // ========================================================================
    // 空闲回收
    // ========================================================================

    /**
     * 起一个巡检线程：关掉空闲太久的会话，并在超出常驻上限时按最久未用淘汰。
     *
     * <p>没有这条，每个聊过的群都会永久留一个 claude 进程。回收代价只是那个聊天丢了上下文，
     * 与"新建进程不带历史"是同一个语义，所以可以放心杀。
     */
    private void startReaper() {
        if (properties.getSessionIdleMinutes() <= 0 && properties.getMaxSessions() <= 0) {
            log.info("会话空闲回收未启用（session-idle-minutes 与 max-sessions 都不大于 0）");
            return;
        }
        ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "claude-session-reaper");
            thread.setDaemon(true);
            return thread;
        });
        timer.scheduleWithFixedDelay(this::reap,
                REAP_INTERVAL_MINUTES, REAP_INTERVAL_MINUTES, TimeUnit.MINUTES);
        reaper = timer;
        log.info("会话空闲回收已启用：每 {} 分钟巡检一次，空闲 {} 分钟回收，每个池常驻上限 {}",
                REAP_INTERVAL_MINUTES, properties.getSessionIdleMinutes(), properties.getMaxSessions());
    }

    private void reap() {
        try {
            closeIdleSessions();
            evictOverflowSessions();
        } catch (Exception e) {
            log.warn("claude 会话巡检失败", e);
        }
    }

    private void closeIdleSessions() {
        long idleMinutes = properties.getSessionIdleMinutes();
        if (idleMinutes <= 0) {
            return;
        }
        long thresholdMillis = TimeUnit.MINUTES.toMillis(idleMinutes);
        for (String key : List.copyOf(sessions.keySet())) {
            ClaudeSession session = sessions.get(key);
            if (session == null || session.isTurnInFlight() || session.idleMillis() < thresholdMillis) {
                continue;
            }
            // 先摘出 map 再关：否则并发取会话的人可能拿到这个正在被关掉的实例
            if (sessions.remove(key, session)) {
                log.info("回收空闲 claude 会话：key={} 已空闲 {} 分钟", key, session.idleMillis() / 60_000L);
                closeQuietly(key, session);
            }
        }
    }

    /** 每个池各自限量：超出就按最久未用（idle 最久）逐个淘汰。 */
    private void evictOverflowSessions() {
        int max = properties.getMaxSessions();
        if (max <= 0) {
            return;
        }
        while (true) {
            String over = overfullPool(max);
            if (over == null || !evictOldest(over)) {
                return;
            }
        }
    }

    // 超出上限的池名；没有就返回 null
    private String overfullPool(int max) {
        Map<String, Integer> counts = new HashMap<>();
        for (ClaudeSession session : sessions.values()) {
            counts.merge(session.pool(), 1, Integer::sum);
        }
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > max) {
                return entry.getKey();
            }
        }
        return null;
    }

    /** 淘汰该池最久未用的一个；池里全在跑（或已被别人摘走）时返回 false，这轮不淘汰。 */
    private boolean evictOldest(String pool) {
        String lruKey = null;
        ClaudeSession lru = null;
        for (Map.Entry<String, ClaudeSession> entry : sessions.entrySet()) {
            ClaudeSession candidate = entry.getValue();
            if (!pool.equals(candidate.pool()) || candidate.isTurnInFlight()) {
                continue;
            }
            if (lru == null || candidate.idleMillis() > lru.idleMillis()) {
                lru = candidate;
                lruKey = entry.getKey();
            }
        }
        if (lru == null || !sessions.remove(lruKey, lru)) {
            return false;
        }
        log.info("池 {} 超出常驻上限，淘汰最久未用的：key={} 已空闲 {} 分钟",
                pool, lruKey, lru.idleMillis() / 60_000L);
        closeQuietly(lruKey, lru);
        return true;
    }

    private void closeQuietly(String key, ClaudeSession session) {
        try {
            session.close();
        } catch (Exception e) {
            log.warn("关闭 claude 会话失败：key={}", key, e);
        }
    }
}
