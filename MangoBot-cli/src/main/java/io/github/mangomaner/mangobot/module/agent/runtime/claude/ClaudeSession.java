package io.github.mangomaner.mangobot.module.agent.runtime.claude;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.mangomaner.mangobot.utils.FileUtils;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * 一个会话 = 一个常驻 claude 进程 + 串行化的轮次。
 *
 * <p>轮次串行由 {@link Semaphore} 保证，而不是 {@code ReentrantLock}：解锁发生在 stdout 读线程
 * （它才是看到 {@code result} 事件的一方），而 {@code ReentrantLock} 不允许跨线程解锁。
 *
 * <p>{@link #sendTurn} 的语义：发出后返回的 Flux 会一直推送该轮的事件，直到收到终止的
 * {@code result} 事件后 complete。因为信号量把轮次串行化了，所以"必须等 result 再发下一轮"
 * 是结构性保证的——第二个并发调用会在 {@code acquire} 上阻塞，而不是把两条消息混进同一个进程。
 *
 * <p><b>起进程之后不要等它"就绪"</b>：stream-json 模式下子进程在收到第一行输入之前一个字节都不吐，
 * 所以任何"等首行输出"的探针都只会白等到超时（实测给每轮首条消息平白加上 30 秒）。
 * 子进程可用性由 spawn 异常、以及进程退出回调带上 stderr 尾部来暴露。
 */
@Slf4j
public class ClaudeSession {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String key;
    private final Path binary;
    private final Path workingDir;
    private final Path configDir;
    private final ClaudeRuntimeProperties properties;
    /** 每次拉起进程时现取端点环境变量；由 {@code ClaudeRuntimeManager} 提供，读「模型设置」的主模型。 */
    private final Supplier<Map<String, String>> endpointEnvSupplier;
    /** 能力相关的命令行参数（--plugin-dir / --mcp-config）；spawn 那一刻定死，改配置要重建会话。 */
    private final Supplier<List<String>> capabilityArgsSupplier;
    /** claude 内置工具白名单（空格分隔）；空串=全禁。judge 会话要一个工具都不给，见 ClaudeRuntimeManager。 */
    private final String builtinTools;
    /**
     * 拉起进程时要不要续上这个工作目录里最近的一次对话（{@code -c}）。
     *
     * <p>正常聊天会话为 {@code true}——重启、改技能重建进程之后，上下文都还在。judge / 探针 / 一次性
     * 会话为 {@code false}：它们每次都必须从干净的上下文开始。
     */
    private final boolean resumeSession;

    private final Semaphore turnGate = new Semaphore(1);
    private final AtomicBoolean turnInFlight = new AtomicBoolean(false);
    /** 最后一次见到动静的时刻，供空闲回收判定 */
    private final AtomicLong lastActivityMillis = new AtomicLong(System.currentTimeMillis());

    private volatile ClaudeProcess process;
    private volatile Sinks.Many<ClaudeEvent> currentSink;

    /** 下次拉起时重建进程的标志，见 {@link #markStale()}。 */
    private volatile boolean stale;

    public ClaudeSession(String key, Path binary, Path workingDir, Path configDir,
                         ClaudeRuntimeProperties properties,
                         Supplier<Map<String, String>> endpointEnvSupplier,
                         Supplier<List<String>> capabilityArgsSupplier,
                         String builtinTools, boolean resumeSession) {
        this.key = key;
        this.binary = binary;
        this.workingDir = workingDir;
        this.configDir = configDir;
        this.properties = properties;
        this.endpointEnvSupplier = endpointEnvSupplier;
        this.capabilityArgsSupplier = capabilityArgsSupplier;
        this.builtinTools = builtinTools;
        this.resumeSession = resumeSession;
    }

    public String key() {
        return key;
    }

    /** 预拉进程（幂等）。仅供 warmup 或 smoke 端点显式调用；正常路径是首轮懒启动。 */
    public synchronized void warmup() {
        ensureProcess();
    }

    /**
     * 发一轮对话，返回该轮的事件流。
     *
     * <p>注意：调用方必须订阅返回的 Flux，否则会在超时后被 {@code timeout} 之外的因素挂住——
     * 实际上轮次终结由进程的 {@code result} 事件驱动，未订阅只是收不到事件，不会死锁。
     *
     * <p><b>本方法会同步抛 {@link IllegalStateException}</b>（进程需要重建但端点已不可用时），
     * 而不是把它变成 Flux 上的 error 信号——因为它发生在 Flux 建好之前。调用方要么先确认
     * {@code runtimeManager.isReady()}，要么自己 try/catch。
     */
    public Flux<ClaudeEvent> sendTurn(String text) {
        return sendTurn(new ClaudeTurnInput(text));
    }

    /** 发一轮带图（或不带）的对话；阻塞等轮次槽位，dev / smoke 端点用。 */
    public Flux<ClaudeEvent> sendTurn(ClaudeTurnInput turn) {
        ensureProcess();
        acquireGate();
        return startTurn(turn);
    }

    /**
     * 非阻塞地发一轮：轮次槽位被别人占着时立刻返回 {@code Optional.empty()}，不排队。
     *
     * <p>回复链路的轮次跑在独立的池上，线程很宝贵，所以不学 {@link #sendTurn} 那样阻塞在信号量上——
     * 该不该等由调用方决定（QQ 侧是"进缓冲等下一轮"，web 侧是直接回一句"正在生成"）。
     *
     * @return 本轮的事件流；没抢到槽位时为空
     */
    public Optional<Flux<ClaudeEvent>> trySendTurn(ClaudeTurnInput turn) {
        ensureProcess();
        if (!turnGate.tryAcquire()) {
            return Optional.empty();
        }
        turnInFlight.set(true);
        return Optional.of(startTurn(turn));
    }

    private Flux<ClaudeEvent> startTurn(ClaudeTurnInput turn) {
        Sinks.Many<ClaudeEvent> sink = Sinks.many().unicast().onBackpressureBuffer();
        currentSink = sink;
        touch();

        try {
            process.writeLine(userTurnJson(turn));
        } catch (IOException e) {
            currentSink = null;
            releaseGate();
            return Flux.error(new IllegalStateException("Failed to write turn to claude process for " + key, e));
        }

        return sink.asFlux()
                .timeout(Duration.ofSeconds(properties.getTurnTimeoutSeconds()))
                .doOnError(e -> {
                    log.error("Turn failed for session {}: {}", key, e.toString());
                    killProcess();
                    releaseGate();
                })
                .doFinally(signal -> {
                    if (currentSink == sink) {
                        currentSink = null;
                    }
                });
    }

    public boolean isRunning() {
        ClaudeProcess p = process;
        return p != null && p.isAlive();
    }

    /** 标一下"这个会话还在被用"，空闲回收据此判断。 */
    public void touch() {
        lastActivityMillis.set(System.currentTimeMillis());
    }

    /** 是否有一轮正在跑。空闲回收据此跳过，免得把在途的一轮掐成半截。 */
    public boolean isTurnInFlight() {
        return turnInFlight.get();
    }

    /** 距上次动静过了多久（毫秒）。 */
    public long idleMillis() {
        return System.currentTimeMillis() - lastActivityMillis.get();
    }

    /**
     * 标记"下次拉起时重建进程"。
     *
     * <p>技能目录（{@code --plugin-dir}）与系统提示词（{@code --system-prompt} /
     * {@code --append-system-prompt}）都是 spawn 那一刻定死的参数，改了配置对已在跑的进程没有任何影响，
     * 所以改完技能或人格得让会话重建才生效。刻意不立刻关进程：正在跑的轮次不该被打断，
     * 重建留给下一次 {@code ensureProcess()}（也就是下一轮对话）。
     */
    public void markStale() {
        stale = true;
    }

    /** stderr 尾部，供诊断把失败原因暴露出来。 */
    public List<String> stderrTail() {
        ClaudeProcess p = process;
        return p == null ? List.of() : p.stderrTail();
    }

    /** 优雅关闭：关 stdin（EOF）等待自行退出，超时强杀。 */
    public synchronized void close() {
        ClaudeProcess p = process;
        if (p == null) {
            return;
        }
        log.info("Closing claude session {} pid={}", key, p.pid());
        p.closeStdin();
        try {
            if (!p.awaitExit(properties.getShutdownGraceMillis())) {
                log.warn("claude session {} did not exit gracefully, killing", key);
                p.destroyForcibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            p.destroyForcibly();
        }
        process = null;
    }

    // ========================================================================
    // 内部
    // ========================================================================

    private synchronized void ensureProcess() {
        if (stale) {
            // 技能目录 / 系统提示词变了：那些都是 spawn 参数，进程起来那一刻就冻住了，只能换进程。
            // 刻意不在这里之外的地方提前关——进行中的轮次不该被打断，重建留给下一次拉起（即下一轮）。
            stale = false;
            if (process != null && process.isAlive()) {
                log.info("会话 {} 的技能/系统提示词有变更，重建进程", key);
                close();
            }
        }
        if (process != null && process.isAlive()) {
            return;
        }
        if (process != null) {
            log.warn("claude process for session {} is dead, respawning", key);
            process.destroyForcibly();
            process = null;
        }

        FileUtils.createDirectory(workingDir);
        FileUtils.createDirectory(configDir);
        // 每个会话一份 config 目录，所以每次拉起都要保证它里面有 .claude.json——缺了
        // hasCompletedOnboarding，claude 会弹主题/登录向导把子进程卡死（-p 只跳过工作区信任，
        // 不跳过 onboarding）。放在这里而不是启动时一次性播，是为了顺带把"删掉 config 目录 =
        // 清空上下文"做成自愈的：清完下一轮拉起时这里会重新播种。
        ClaudeConfigSeeder.seed(configDir, workingDir);

        List<String> args = new ArrayList<>();
        List<String> capabilityArgs = capabilityArgsSupplier != null ? capabilityArgsSupplier.get() : null;
        if (capabilityArgs != null) {
            args.addAll(capabilityArgs);
        }
        if (properties.getExtraArgs() != null) {
            args.addAll(properties.getExtraArgs());
        }

        ClaudeProcess started;
        try {
            started = ClaudeProcess.start(binary, workingDir, configDir,
                    endpointEnvSupplier.get(), args, builtinTools, resumeSession);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to start claude process for session " + key, e);
        }

        started.setEventConsumer(this::onEvent);
        started.setExitCallback(code -> onProcessExit(started, code));
        process = started;
        log.info("Claude session {} spawned pid={} cwd={}", key, started.pid(), workingDir);
    }

    private void onEvent(ClaudeEvent event) {
        Sinks.Many<ClaudeEvent> sink = currentSink;
        if (sink != null) {
            sink.tryEmitNext(event);
        }
        if (event.isResult()) {
            touch();
            if (sink != null) {
                sink.tryEmitComplete();
            }
            releaseGate();
        }
    }

    private void onProcessExit(ClaudeProcess exited, int code) {
        if (process != exited) {
            // 已被替换掉的旧进程，忽略
            return;
        }
        Sinks.Many<ClaudeEvent> sink = currentSink;
        if (sink != null) {
            sink.tryEmitError(new IllegalStateException(
                    "claude process exited with code " + code
                            + "; stderr tail: " + String.join(" | ", exited.stderrTail())));
        }
        releaseGate();
    }

    private void acquireGate() {
        try {
            turnGate.acquire();
            turnInFlight.set(true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for a turn slot on session " + key, e);
        }
    }

    private void releaseGate() {
        if (turnInFlight.compareAndSet(true, false)) {
            turnGate.release();
        }
    }

    private void killProcess() {
        ClaudeProcess p = process;
        if (p != null) {
            p.destroyForcibly();
        }
    }

    private String userTurnJson(ClaudeTurnInput turn) throws IOException {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("type", "user");
        ObjectNode message = root.putObject("message");
        message.put("role", "user");
        ArrayNode content = message.putArray("content");

        // 图在前、文本在后（Anthropic 推荐的顺序）
        if (turn.images() != null) {
            for (ClaudeImage image : turn.images()) {
                ObjectNode block = content.addObject();
                block.put("type", "image");
                ObjectNode source = block.putObject("source");
                source.put("type", "base64");
                source.put("media_type", image.mediaType());
                source.put("data", image.base64Data());
            }
        }

        ObjectNode block = content.addObject();
        block.put("type", "text");
        block.put("text", turn.text() == null ? "" : turn.text());
        return MAPPER.writeValueAsString(root);
    }
}
