package io.github.mangomaner.mangobot.module.agent.core;

import io.github.mangomaner.mangobot.module.agent.model.enums.SessionSource;
import io.github.mangomaner.mangobot.utils.FileUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Workspace / 全局能力目录管理器
 *
 * <p>工作区按 (Bot, 来源) 拆分（Web/群聊/私聊），作为 Agent 的文件系统根目录与状态/技能存储。
 * 人格与行为不由工作区文件承载（AGENTS.md 已退役，行为来源是技能），
 * 工作区不再播种 AGENTS.md——已退役文件会被迁移删除，避免旧人格注入与空文件噪音。
 *
 * <p>全局能力目录：data/capabilities/skills/（技能内容，所有 Bot 共用）。该目录本身是交给 claude 的
 * 插件目录，技能必须落在它下面的 skills/ 子目录里才会被识别；
 * AgentState 存储：data/state/。能力定义（工具/MCP/Skill 元数据）以 DB 为 source of truth。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentWorkspaceManager {

    private static final String WORKSPACES_DIR = "data/workspaces";
    private static final String CAPABILITIES_DIR = "data/capabilities";
    /** 插件目录（claude --plugin-dir 的值） */
    private static final String PLUGIN_DIR = "data/capabilities/skills";
    /** 插件目录下真正存技能内容的子目录 */
    private static final String SKILLS_DIR = PLUGIN_DIR + "/skills";
    private static final String STATE_DIR = "data/state";

    /** 升级前技能内容所在目录，首次启动迁移到全局能力目录 */
    private static final String LEGACY_SKILLS_DIR = "data/skills";

    /** persona 版本标记：AGENTS.md 已退役（v3），不再播种，仅保留版本文件记录迁移状态 */
    private static final String PERSONA_VERSION_FILE = ".persona-version";
    private static final String PERSONA_VERSION = "3";

    /** Web 端工作区名（botId 为空时 sanitize 为 default） */
    private static final String WEB_WORKSPACE = "default";

    private static final String GROUP_WORKSPACE = "group";
    private static final String PRIVATE_WORKSPACE = "private";

    /**
     * 启动时初始化：确保全局能力目录存在、迁移旧技能目录。
     * 由 MangoBotApplication.main 显式调用（Bean 内禁止 @PostConstruct 业务初始化）。
     */
    public void init() {
        FileUtils.createDirectory(getCapabilitiesDir());
        FileUtils.createDirectory(getPluginDirectory());
        Path skillsDir = getSkillsDirectory();
        migrateLegacySkills(skillsDir);
        FileUtils.createDirectory(skillsDir);
        FileUtils.createDirectory(getStateDir());
        // 为所有已存在的 Bot 工作区做旧 IM 布局迁移 + 兜底播种 AGENTS.md（缺失时不播种，
        // overlay 会兜底注入 project 根目录的 AGENTS.md——即项目开发文档，极大浪费 token）
        for (String botId : listBotWorkspaces()) {
            Path botRoot = resolveBotWorkspace(botId);
            migrateLegacySourceWorkspace(botRoot);
            ensureBotWorkspace(botId);
            // 重开内置 WorkspaceContextMiddleware 后，清掉存量工作区里残留的旧人格 AGENTS.md
            migrateAgentsMdPlaceholder(botRoot);
            migrateAgentsMdPlaceholder(botRoot.resolve(GROUP_WORKSPACE));
            migrateAgentsMdPlaceholder(botRoot.resolve(PRIVATE_WORKSPACE));
        }
        log.info("Agent workspace initialized: capabilities={}, plugin={}, skills={}, state={}",
                getCapabilitiesDir(), getPluginDirectory(), skillsDir, getStateDir());
    }

    /** 全局能力目录（DB 定义之外的技能内容文件） */
    public Path getCapabilitiesDir() {
        return FileUtils.resolvePath(CAPABILITIES_DIR);
    }

    /** 技能插件目录（交给 claude 的 --plugin-dir） */
    public Path getPluginDirectory() {
        return FileUtils.resolvePath(PLUGIN_DIR);
    }

    /** 全局技能内容目录（插件目录下的 skills/ 子目录） */
    public Path getSkillsDirectory() {
        return FileUtils.resolvePath(SKILLS_DIR);
    }

    /** AgentState 本地存储目录 */
    public Path getStateDir() {
        return FileUtils.resolvePath(STATE_DIR);
    }

    /** 解析 Bot 工作区根目录（不创建） */
    public Path resolveBotWorkspace(String botId) {
        return FileUtils.resolvePath(WORKSPACES_DIR).resolve(sanitize(botId));
    }

    /** 解析 (Bot, 来源) 的 Agent 工作区（不创建）：group/private 为根目录下子工作区，Web 即根目录 */
    public Path resolveAgentWorkspace(String botId, SessionSource source) {
        Path root = resolveBotWorkspace(botId);
        SessionSource src = source != null ? source : SessionSource.WEB;
        if (src == SessionSource.GROUP) {
            return root.resolve(GROUP_WORKSPACE);
        }
        if (src == SessionSource.PRIVATE) {
            return root.resolve(PRIVATE_WORKSPACE);
        }
        return root;
    }

    /**
     * 确保指定 (Bot, 来源) 的 Agent 工作区存在并播种对应来源默认人格，返回工作区路径。
     * <ul>
     *   <li>Web：播种 Web 人格 AGENTS.md</li>
     *   <li>群聊/私聊：确保根工作区 + 对应子工作区 AGENTS.md（来源默认人格）</li>
     * </ul>
     */
    public Path ensureAgentWorkspace(String botId, SessionSource source) {
        SessionSource src = source != null ? source : SessionSource.WEB;
        ensureBotWorkspace(botId);
        if (src == SessionSource.GROUP || src == SessionSource.PRIVATE) {
            ensureSourceWorkspace(botId, src);
        }
        return resolveAgentWorkspace(botId, src);
    }

    /**
     * 确保 Bot 工作区根目录存在并播种 Web 人格 AGENTS.md；非 Web 端 Bot 顺带确保群聊/私聊子工作区。
     */
    public Path ensureBotWorkspace(String botId) {
        Path workspace = resolveBotWorkspace(botId);
        FileUtils.createDirectory(workspace);
        seedAgentsMd(workspace);
        if (!isWebWorkspace(botId)) {
            ensureSourceWorkspace(botId, SessionSource.GROUP);
            ensureSourceWorkspace(botId, SessionSource.PRIVATE);
        }
        return workspace;
    }

    /** 列出所有已初始化的 Bot 工作区（仅根目录，不含 group/private 子工作区） */
    public List<String> listBotWorkspaces() {
        List<String> result = new ArrayList<>();
        Path root = FileUtils.resolvePath(WORKSPACES_DIR);
        FileUtils.createDirectory(root);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
            for (Path child : stream) {
                if (Files.isDirectory(child)) {
                    result.add(child.getFileName().toString());
                }
            }
        } catch (IOException e) {
            log.error("Failed to list bot workspaces", e);
        }
        return result;
    }

    /**
     * 旧 IM 布局迁移（一次性，幂等）：AGENTS_GROUP.md / AGENTS_PRIVATE.md 迁为 group/private
     * 子工作区的 AGENTS.md；根目录下 bot_&lt;botId&gt;_group_* / _private_* 会话目录迁入对应子工作区。
     */
    private void migrateLegacySourceWorkspace(Path botWs) {
        try {
            Path groupDir = botWs.resolve(GROUP_WORKSPACE);
            Path privateDir = botWs.resolve(PRIVATE_WORKSPACE);
            boolean migrated = false;
            migrated |= moveLegacyDefaultFile(botWs.resolve("AGENTS_GROUP.md"), groupDir);
            migrated |= moveLegacyDefaultFile(botWs.resolve("AGENTS_PRIVATE.md"), privateDir);

            // 先收集再移动，避免在 DirectoryStream 迭代期间改动目录
            List<Path> sessionDirs = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(botWs)) {
                for (Path child : stream) {
                    String name = child.getFileName().toString();
                    if (!Files.isDirectory(child) || !name.startsWith("bot_")) {
                        continue;
                    }
                    if (name.contains("_group_") || name.contains("_private_")) {
                        sessionDirs.add(child);
                    }
                }
            }
            for (Path child : sessionDirs) {
                String name = child.getFileName().toString();
                Path targetDir = name.contains("_group_") ? groupDir : privateDir;
                FileUtils.createDirectory(targetDir);
                Files.move(child, targetDir.resolve(name));
                migrated = true;
            }
            if (migrated) {
                log.info("Migrated legacy IM workspace layout: {}", botWs);
            }
        } catch (IOException e) {
            log.error("Failed to migrate legacy workspace: {}", botWs, e);
        }
    }

    /** 迁移旧版来源默认人格文件到子工作区 AGENTS.md；目标已存在时丢弃旧文件。 */
    private boolean moveLegacyDefaultFile(Path source, Path targetDir) throws IOException {
        if (!Files.exists(source)) {
            return false;
        }
        FileUtils.createDirectory(targetDir);
        Path target = targetDir.resolve("AGENTS.md");
        if (Files.exists(target)) {
            Files.deleteIfExists(source);
        } else {
            Files.move(source, target);
            // 写版本标记，避免 ensureSourceWorkspace 因版本缺失用模板覆盖用户默认人格
            FileUtils.writeString(targetDir.resolve(PERSONA_VERSION_FILE), PERSONA_VERSION);
        }
        return true;
    }

    /** 将 botId 规整为文件系统安全段 */
    private static String sanitize(String botId) {
        if (!StringUtils.hasText(botId)) {
            return "default";
        }
        return botId.replaceAll("[^A-Za-z0-9_-]", "_");
    }

    /** Web 端：botId 为空，或工作区名恰为 default（init 迁移存量工作区时传入的是目录名） */
    private static boolean isWebWorkspace(String botId) {
        return !StringUtils.hasText(botId) || WEB_WORKSPACE.equalsIgnoreCase(botId);
    }

    /**
     * Web 工作区不再播种 AGENTS.md（行为由技能承载），仅记录 persona 版本标记。
     */
    private void seedAgentsMd(Path workspace) {
        FileUtils.writeString(workspace.resolve(PERSONA_VERSION_FILE), PERSONA_VERSION);
    }

    /** 确保子工作区存在并记录 persona 版本标记（AGENTS.md 已退役，不再播种） */
    private Path ensureSourceWorkspace(String botId, SessionSource source) {
        Path ws = resolveAgentWorkspace(botId, source);
        FileUtils.createDirectory(ws);
        FileUtils.writeString(ws.resolve(PERSONA_VERSION_FILE), PERSONA_VERSION);
        return ws;
    }

    /**
     * AGENTS.md 已退役（行为由技能承载）：删除工作区里残留的 AGENTS.md。
     * 空文件会让内置 WorkspaceContextMiddleware 注入 "File exists but has empty contents" 噪音，
     * 故直接删除文件而非清空。幂等：删除后不存在，后续启动跳过。
     */
    private void migrateAgentsMdPlaceholder(Path workspace) {
        if (!Files.isDirectory(workspace)) {
            return;
        }
        Path agentsMd = workspace.resolve("AGENTS.md");
        if (Files.isRegularFile(agentsMd)) {
            try {
                Files.deleteIfExists(agentsMd);
                log.info("Removed legacy AGENTS.md from workspace: {}", workspace);
            } catch (IOException e) {
                log.warn("Failed to remove AGENTS.md at {}: {}", workspace, e.toString());
            }
        }
        Path versionFile = workspace.resolve(PERSONA_VERSION_FILE);
        if (!Files.exists(versionFile)) {
            FileUtils.writeString(versionFile, PERSONA_VERSION);
        }
    }

    private void migrateLegacySkills(Path targetSkillsDir) {
        Path legacy = FileUtils.resolvePath(LEGACY_SKILLS_DIR);
        if (!Files.isDirectory(legacy) || Files.exists(targetSkillsDir)) {
            return;
        }
        try {
            Files.createDirectories(targetSkillsDir);
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(legacy)) {
                for (Path child : stream) {
                    copyRecursively(child, targetSkillsDir.resolve(child.getFileName().toString()));
                }
            }
            log.info("Migrated legacy skills from {} to {}", legacy, targetSkillsDir);
        } catch (IOException e) {
            log.error("Failed to migrate legacy skills", e);
        }
    }

    private void copyRecursively(Path source, Path target) throws IOException {
        if (Files.isDirectory(source)) {
            Files.createDirectories(target);
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(source)) {
                for (Path child : stream) {
                    copyRecursively(child, target.resolve(child.getFileName().toString()));
                }
            }
        } else {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

}
