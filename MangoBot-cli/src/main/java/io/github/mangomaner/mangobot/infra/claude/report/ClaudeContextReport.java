package io.github.mangomaner.mangobot.infra.claude.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mangomaner.mangobot.infra.claude.session.ClaudeLaunchCommand;
import io.github.mangomaner.mangobot.infra.claude.spec.ClaudeSessionSpec;
import io.github.mangomaner.mangobot.infra.claude.workspace.ClaudeWorkspace;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 跑 {@code claude -p /context}，让 CLI 给出上下文分类明细（系统提示词/工具/技能各占多少——
 * 那些是它内部的估算，转录里没有）。
 *
 * <p><b>必须跑在副本上</b>：实测 {@code -c} 会把这条命令连同输出写进转录，对活跃会话跑等于往 bot
 * 的对话里插一轮它自己没说过的话。所以先复制一份 config 目录，在副本上跑，跑完删。
 *
 * <p>参数与真实会话同源（同一份 {@link ClaudeSessionSpec}）：少给一个，那份报告算的就是另一套配置的
 * 占比，看着像真的却是错的。
 */
@Slf4j
public final class ClaudeContextReport {

    // 纯本地计算，正常几秒内就回
    private static final long TIMEOUT_SECONDS = 60L;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ClaudeContextReport() {
    }

    /**
     * @param spec 会话配置；端点不可用时它自己会抛，由调用方转成可读消息
     * @return CLI 输出的 markdown；跑不了时返回一句可读的说明
     */
    public static String run(Path binary, ClaudeWorkspace workspace, String key,
                             Supplier<ClaudeSessionSpec> spec) {
        if (binary == null || !Files.isExecutable(binary)) {
            return "跑不了：claude 可执行文件不可用。";
        }
        ClaudeSessionSpec launch = spec.get();

        Path configDir = workspace.configDirOf(key);
        if (!Files.isDirectory(configDir)) {
            return "跑不了：这个会话还没有上下文目录（没跑过任何一轮）。";
        }

        Path tempRoot = null;
        try {
            tempRoot = Files.createTempDirectory("mango-ctx-");
            Path tempConfig = tempRoot.resolve(ClaudeWorkspace.CLAUDE_CONFIG_SUBDIR);
            copyTree(configDir, tempConfig);

            ProcessBuilder builder = new ProcessBuilder(command(binary, workspace, key, launch));
            builder.directory(workspace.dirOf(key).toFile());
            builder.environment().putAll(launch.env());
            // 指向副本：CLI 要往转录里写什么，都写进副本
            builder.environment().put("CLAUDE_CONFIG_DIR", tempConfig.toString());

            Process process = builder.start();
            String stdout = new String(process.getInputStream().readAllBytes());
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return "跑超时了（" + TIMEOUT_SECONDS + " 秒）。";
            }
            return extractReport(stdout);
        } catch (Exception e) {
            log.warn("跑 /context 失败：key={}", key, e);
            return "跑失败了：" + e.getMessage();
        } finally {
            deleteQuietly(tempRoot);
        }
    }

    /** 与真实会话同一套参数，另加 /context 自己的前缀与 {@code --tools}。 */
    private static List<String> command(Path binary, ClaudeWorkspace workspace, String key,
                                        ClaudeSessionSpec spec) {
        List<String> command = new ArrayList<>(List.of(
                binary.toString(), "-p", "/context", "--output-format", "json"));
        command.addAll(ClaudeLaunchCommand.argsOf(spec, workspace, key));
        command.add("--tools");
        command.add(String.join(" ", spec.builtinTools()));
        command.add("-c");
        return command;
    }

    /** 从 {@code {"result": "## Context Usage ..."}} 里取出报告正文。 */
    private static String extractReport(String stdout) {
        try {
            JsonNode root = MAPPER.readTree(stdout);
            String result = root.path("result").asText("");
            return result.isBlank() ? "CLI 没有返回内容。" : result;
        } catch (Exception e) {
            return stdout.isBlank() ? "CLI 没有返回内容。" : stdout;
        }
    }

    private static void copyTree(Path from, Path to) throws IOException {
        Files.walkFileTree(from, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(to.resolve(from.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, to.resolve(from.relativize(file).toString()));
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void deleteQuietly(Path root) {
        if (root == null) {
            return;
        }
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // 删不掉就算了，临时目录，不打断请求
                }
            });
        } catch (IOException e) {
            log.warn("临时上下文目录没删干净：{}", root, e);
        }
    }
}
