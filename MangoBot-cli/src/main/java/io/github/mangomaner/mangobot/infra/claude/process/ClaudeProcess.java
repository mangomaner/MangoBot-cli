package io.github.mangomaner.mangobot.infra.claude.process;

import io.github.mangomaner.mangobot.infra.claude.ClaudeRuntimeProperties;
import io.github.mangomaner.mangobot.infra.claude.session.ClaudeEvent;
import io.github.mangomaner.mangobot.infra.claude.session.ClaudeSession;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 一个常驻的 claude 子进程，用 {@code --input-format stream-json} 在一个进程里跑多轮对话。
 *
 * <p>关键实现约束：
 * <ul>
 *   <li><b>必须两个读线程</b>：{@code redirectErrorStream(false)} 时不排空 stderr 管道会填满并死锁子进程。</li>
 *   <li>stdout 每行是一个独立的 JSON 对象，逐行解析后交给 {@link #setEventConsumer} 注册的消费者。</li>
 *   <li>非 JSON 行（如启动时的横幅）跳过并打 debug 日志，不视为错误。</li>
 * </ul>
 *
 * <p>本类不含任何响应式代码：它只负责"起进程、喂 stdin、读 stdout"，轮次编排在 {@link ClaudeSession}。
 */
@Slf4j
public class ClaudeProcess {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int STDERR_TAIL_LIMIT = 50;

    private final Process process;
    private final BufferedWriter stdin;
    private final Path binary;
    private final AtomicBoolean alive = new AtomicBoolean(true);
    private final Object stdinLock = new Object();
    private final Deque<String> stderrTail = new ArrayDeque<>();

    private volatile Consumer<ClaudeEvent> eventConsumer = event -> {
    };
    private volatile Consumer<Integer> exitCallback = code -> {
    };

    private ClaudeProcess(Process process, Path binary) {
        this.process = process;
        this.binary = binary;
        this.stdin = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        startStdoutReader();
        startStderrReader();
        startExitWatcher();
    }

    /**
     * 拉起一个 claude 进程。
     *
     * @param binary       可执行文件路径
     * @param workingDir   子进程工作目录
     * @param configDir    作为 {@code CLAUDE_CONFIG_DIR} 注入，隔离于用户个人配置
     * @param endpointEnv  端点配置（base url / 密钥 / 模型），显式覆盖环境里继承来的同名变量
     * @param extraArgs      追加到命令行末尾的额外参数
     * @param builtinTools   允许模型使用的 claude 内置工具，空格分隔的白名单；{@code null} 或空串表示一个都不给
     * @param resumeSession  是否续上这个工作目录里最近的一次对话（{@code -c}）。见下面 {@code -c} 那段说明
     */
    public static ClaudeProcess start(Path binary, Path workingDir, Path configDir,
                                      Map<String, String> endpointEnv, List<String> extraArgs,
                                      String builtinTools, boolean resumeSession)
            throws IOException {
        List<String> command = new ArrayList<>();
        command.add(binary.toString());
        command.add("-p");
        command.add("--input-format");
        command.add("stream-json");
        command.add("--output-format");
        command.add("stream-json");
        command.add("--verbose");
        command.add("--include-partial-messages");
        // 用白名单而非逐个 deny：Windows 上的 shell 工具叫 PowerShell 不叫 Bash，按名字 deny 会漏。
        // 默认只放 Skill（见 ClaudeRuntimeProperties.builtinTools）：技能正文要靠它按需加载，
        // 压掉它 claude 会把技能广告整段从系统提示词里撤掉（等于技能体系瘫掉）；同时其余 20 多个
        // 工具 schema 不进提示词，实测输入 token 从 23054 降到 5390 量级。
        // 空串 = 一个都不给（fail closed），测评的 judge 会话走这条。
        command.add("--tools");
        command.add(builtinTools == null ? "" : builtinTools);

        // {@code -c}（continue）= 续上"这个工作目录里最近的一次对话"。claude 一直把每场对话完整写在
        // {@code <configDir>/projects/<cwd 的 slug>/<sessionId>.jsonl}，只是此前我们从不接上去——
        // 于是每次进程重建（重启、改技能）都另起一条，上下文看起来"丢了"。
        //
        // 为什么是 -c 而不是 --session-id/--resume：会话与工作目录本来就是一对一（一个群一个目录），
        // 用 -c 零簿记；而 --session-id 要求自己生成合法 UUID、还得判断"这是第一次还是续"。
        // 实测（2026-09-26，claude 2.1.128）：-p --input-format stream-json 下 -c 能续上；
        // 且在全新的空目录上用 -c 不报错，直接开一场新对话——所以不需要"先看有没有转录"的分支。
        //
        // 为什么做成开关而不是无条件加：judge 会话每次评判都必须独立（同一个 key 重跑时续上
        // 上一次的评判会污染结论），探针也没必要续。见 ClaudeSession 的续会话标志。
        if (resumeSession) {
            command.add("-c");
        }

        if (extraArgs != null) {
            command.addAll(extraArgs);
        }

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workingDir.toFile());
        pb.redirectErrorStream(false);

        // 只改子进程环境，绝不碰 JVM 自身的进程环境
        Map<String, String> env = pb.environment();
        env.put("CLAUDE_CONFIG_DIR", configDir.toString());
        env.put("DISABLE_AUTOUPDATER", "1");
        if (endpointEnv != null) {
            // 放在最后：配置要覆盖掉环境里继承来的同名变量
            env.putAll(endpointEnv);
        }

        Process process = pb.start();
        log.info("Started claude process pid={} binary={} cwd={}", process.pid(), binary, workingDir);
        return new ClaudeProcess(process, binary);
    }

    /** 注册 stdout 事件的消费者。 */
    public void setEventConsumer(Consumer<ClaudeEvent> consumer) {
        this.eventConsumer = consumer == null ? event -> {
        } : consumer;
    }

    /** 注册进程退出回调，参数为退出码。 */
    public void setExitCallback(Consumer<Integer> callback) {
        this.exitCallback = callback == null ? code -> {
        } : callback;
    }

    /** 写入一行 JSON 到 stdin。 */
    public void writeLine(String json) throws IOException {
        synchronized (stdinLock) {
            stdin.write(json);
            stdin.write('\n');
            stdin.flush();
        }
    }

    public boolean isAlive() {
        return alive.get() && process.isAlive();
    }

    public long pid() {
        return process.pid();
    }

    public Path binary() {
        return binary;
    }

    /** stderr 的尾部若干行，用于把启动失败的原因暴露出来。 */
    public List<String> stderrTail() {
        synchronized (stderrTail) {
            return new ArrayList<>(stderrTail);
        }
    }

    /** 关闭 stdin（优雅 EOF），让进程处理完在途工作后自行退出。 */
    public void closeStdin() {
        synchronized (stdinLock) {
            try {
                stdin.close();
            } catch (IOException e) {
                log.debug("Failed to close claude stdin", e);
            }
        }
    }

    /** 等待进程退出，返回是否在超时前退出。 */
    public boolean awaitExit(long millis) throws InterruptedException {
        return process.waitFor(millis, TimeUnit.MILLISECONDS);
    }

    /** 强杀进程。 */
    public void destroyForcibly() {
        closeStdin();
        if (process.isAlive()) {
            process.destroyForcibly();
        }
    }

    private void startStdoutReader() {
        Thread t = new Thread(this::readStdout, "claude-stdout-" + process.pid());
        t.setDaemon(true);
        t.start();
    }

    private void startStderrReader() {
        Thread t = new Thread(this::readStderr, "claude-stderr-" + process.pid());
        t.setDaemon(true);
        t.start();
    }

    private void startExitWatcher() {
        Thread t = new Thread(() -> {
            try {
                int code = process.waitFor();
                alive.set(false);
                log.info("Claude process pid={} exited with code={}", process.pid(), code);
                exitCallback.accept(code);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "claude-exit-" + process.pid());
        t.setDaemon(true);
        t.start();
    }

    private void readStdout() {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                dispatch(line);
            }
        } catch (IOException e) {
            if (alive.get()) {
                log.warn("claude stdout reader ended abnormally pid={}", process.pid(), e);
            }
        }
    }

    private void dispatch(String line) {
        if (line.isBlank()) {
            return;
        }
        JsonNode node;
        try {
            node = MAPPER.readTree(line);
        } catch (Exception e) {
            // 非 JSON 行（横幅、警告）属正常现象，不影响协议
            log.debug("Non-JSON line from claude pid={}: {}", process.pid(), line);
            return;
        }
        try {
            eventConsumer.accept(new ClaudeEvent(node, System.currentTimeMillis()));
        } catch (Exception e) {
            log.error("Claude event consumer failed pid={}", process.pid(), e);
        }
    }

    private void readStderr() {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                synchronized (stderrTail) {
                    stderrTail.addLast(line);
                    while (stderrTail.size() > STDERR_TAIL_LIMIT) {
                        stderrTail.removeFirst();
                    }
                }
                log.debug("claude stderr pid={}: {}", process.pid(), line);
            }
        } catch (IOException e) {
            log.debug("claude stderr reader ended pid={}", process.pid(), e);
        }
    }
}
