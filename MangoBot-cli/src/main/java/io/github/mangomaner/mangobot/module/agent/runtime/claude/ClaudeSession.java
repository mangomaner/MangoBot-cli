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
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
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

    private final Semaphore turnGate = new Semaphore(1);
    private final AtomicBoolean turnInFlight = new AtomicBoolean(false);

    private volatile ClaudeProcess process;
    private volatile Sinks.Many<ClaudeEvent> currentSink;

    public ClaudeSession(String key, Path binary, Path workingDir, Path configDir,
                         ClaudeRuntimeProperties properties,
                         Supplier<Map<String, String>> endpointEnvSupplier) {
        this.key = key;
        this.binary = binary;
        this.workingDir = workingDir;
        this.configDir = configDir;
        this.properties = properties;
        this.endpointEnvSupplier = endpointEnvSupplier;
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
        ensureProcess();
        acquireGate();

        Sinks.Many<ClaudeEvent> sink = Sinks.many().unicast().onBackpressureBuffer();
        currentSink = sink;

        try {
            process.writeLine(userTurnJson(text));
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

        ClaudeProcess started;
        try {
            started = ClaudeProcess.start(binary, workingDir, configDir,
                    endpointEnvSupplier.get(), properties.getExtraArgs());
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

    private String userTurnJson(String text) throws IOException {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("type", "user");
        ObjectNode message = root.putObject("message");
        message.put("role", "user");
        ArrayNode content = message.putArray("content");
        ObjectNode block = content.addObject();
        block.put("type", "text");
        block.put("text", text);
        return MAPPER.writeValueAsString(root);
    }
}
