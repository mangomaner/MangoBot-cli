package io.github.mangomaner.mangobot.module.agent.runtime.claude;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.mangomaner.mangobot.utils.FileUtils;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 幂等播种 {@code <configDir>/.claude.json}。
 *
 * <p><b>不播种会挂住子进程</b>：全新的配置目录若没有 {@code hasCompletedOnboarding}，
 * claude 启动时会弹主题/登录向导并卡在那里。{@code -p} 只跳过工作区信任对话框，
 * 不跳过 onboarding。
 *
 * <p>之所以不用 {@link FileUtils#copyResource}：那个方法遇到目标文件已存在就直接返回，
 * 无法"合并"进已有配置，会把用户/前次运行写进去的字段覆盖掉。
 */
@Slf4j
public final class ClaudeConfigSeeder {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String CONFIG_FILE = ".claude.json";

    private ClaudeConfigSeeder() {
    }

    /**
     * 播种/合并配置文件，可重复调用。
     *
     * @param configDir  作为 CLAUDE_CONFIG_DIR 的目录
     * @param workingDir 子进程工作目录，用于写入工作区信任标记
     */
    public static void seed(Path configDir, Path workingDir) {
        FileUtils.createDirectory(configDir);
        Path file = configDir.resolve(CONFIG_FILE);

        ObjectNode root = readExisting(file);
        root.put("hasCompletedOnboarding", true);
        if (!root.hasNonNull("theme")) {
            root.put("theme", "dark");
        }
        seedWorkspaceTrust(root, workingDir);

        try {
            FileUtils.writeString(file, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to seed claude config: " + file, e);
        }
        log.info("Seeded claude config at {}", file.toAbsolutePath());
    }

    private static ObjectNode readExisting(Path file) {
        if (!Files.isRegularFile(file)) {
            return MAPPER.createObjectNode();
        }
        try {
            JsonNode existing = MAPPER.readTree(Files.readString(file, StandardCharsets.UTF_8));
            if (existing != null && existing.isObject()) {
                return (ObjectNode) existing;
            }
            log.warn("Existing claude config at {} is not a JSON object, rebuilding", file.toAbsolutePath());
        } catch (Exception e) {
            log.warn("Existing claude config at {} is unreadable, rebuilding", file.toAbsolutePath(), e);
        }
        return MAPPER.createObjectNode();
    }

    /**
     * 写入工作区信任标记。
     *
     * <p>注意：{@code -p} 模式本身已跳过信任对话框，这里属于双保险；且键名归一化方式
     * 未经官方文档确认，若实测仍被信任提示挡住，问题不在这里。
     */
    private static void seedWorkspaceTrust(ObjectNode root, Path workingDir) {
        String workspace = workingDir.toAbsolutePath().normalize().toString();
        ObjectNode projects = objectChild(root, "projects");
        ObjectNode entry = objectChild(projects, workspace);
        entry.put("hasTrustDialogAccepted", true);
        entry.put("hasCompletedProjectOnboarding", true);
    }

    private static ObjectNode objectChild(ObjectNode parent, String field) {
        JsonNode child = parent.path(field);
        return child.isObject() ? (ObjectNode) child : parent.putObject(field);
    }
}
