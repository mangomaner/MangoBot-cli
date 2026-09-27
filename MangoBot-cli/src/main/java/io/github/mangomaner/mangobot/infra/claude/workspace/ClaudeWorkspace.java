package io.github.mangomaner.mangobot.infra.claude.workspace;

import io.github.mangomaner.mangobot.utils.FileUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * 会话工作区的布局与生命周期——所有路径拼接的唯一实现，外部不要再自己算。
 *
 * <pre>
 * &lt;root&gt;/&lt;key 消毒后&gt;/
 * ├── mcp.json                        # --mcp-config 的值
 * └── claude-config/                  # CLAUDE_CONFIG_DIR
 *     ├── .claude.json                # 每次拉起前必播，缺了会卡 onboarding
 *     └── projects/&lt;cwd 的 slug&gt;/&lt;sessionId&gt;.jsonl
 * </pre>
 *
 * <p>为什么每会话一个 config 目录而不是共用一个：转录写在 {@code <configDir>/projects/<cwd 的 slug>/} 下，
 * 那段目录名由 claude 自己按 cwd 算（归一化规则没公开文档）。要让"清空上下文"变成"删掉这个会话自己的
 * 目录"而不是去猜它的内部命名，就只能每会话一份。
 *
 * <p>key 由调用方给、本类不解析，只负责消毒成目录名。
 */
@Slf4j
public class ClaudeWorkspace {

    // 会话自己那份 claude 家目录，落在该会话的工作目录下
    public static final String CLAUDE_CONFIG_SUBDIR = "claude-config";

    // 会话目录下生成的 MCP 配置文件，即 --mcp-config 的值
    public static final String MCP_CONFIG_FILE = "mcp.json";

    private final Path root;

    public ClaudeWorkspace(Path root) {
        this.root = root;
    }

    /** 按配置取工作区根目录：配了就用配置的，否则用兜底相对路径。 */
    public static ClaudeWorkspace at(String configuredRoot, String fallbackRelative) {
        Path path = configuredRoot != null && !configuredRoot.isBlank()
                ? Paths.get(configuredRoot.trim()).toAbsolutePath().normalize()
                : FileUtils.resolvePath(fallbackRelative);
        return new ClaudeWorkspace(path);
    }

    public Path root() {
        return root;
    }

    // 某个会话的工作目录
    public Path dirOf(String key) {
        return root.resolve(safeName(key));
    }

    // 某个会话的 claude 家目录（CLAUDE_CONFIG_DIR），转录也在它下面
    public Path configDirOf(String key) {
        return dirOf(key).resolve(CLAUDE_CONFIG_SUBDIR);
    }

    // 某个会话的 MCP 配置文件路径
    public Path mcpFileOf(String key) {
        return dirOf(key).resolve(MCP_CONFIG_FILE);
    }

    // 转录所在的上层目录；具体文件名由 claude 自己按 cwd 算，见 ClaudeTranscript
    public Path projectsDirOf(String key) {
        return configDirOf(key).resolve("projects");
    }

    // 建出会话目录（mcp.json 与 claude 家目录都靠它）
    public void ensure(String key) {
        FileUtils.createDirectory(dirOf(key));
    }

    /**
     * 删掉某个会话的家目录——即"清空它的上下文"。
     *
     * <p>只关进程不够：重新拉起时带 {@code -c}，claude 会把磁盘上那份转录又续回来，
     * 用户会看到"点了清空但没生效"。
     *
     * @return 是否真的删掉了什么
     */
    public boolean deleteConfigDir(String key) {
        return deleteRecursively(configDirOf(key));
    }

    // 删掉整个会话目录，连 mcp.json 一起
    public boolean deleteAll(String key) {
        return deleteRecursively(dirOf(key));
    }

    // key 里 Windows 路径段不允许的字符一律换掉
    public static String safeName(String key) {
        return key.replaceAll("[^A-Za-z0-9_-]", "_");
    }

    private boolean deleteRecursively(Path dir) {
        if (!Files.isDirectory(dir)) {
            return false;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    log.warn("删除会话目录时出错：{}", p, e);
                }
            });
            log.info("已删除会话目录：{}", dir);
            return true;
        } catch (IOException e) {
            log.error("删除会话目录失败：{}", dir, e);
            return false;
        }
    }
}
