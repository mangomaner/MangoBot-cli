package io.github.mangomaner.mangobot.module.agent.capability.skill;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mangomaner.mangobot.module.agent.model.domain.AgentSkillConfig;
import io.github.mangomaner.mangobot.module.agent.model.enums.SessionSource;
import io.github.mangomaner.mangobot.module.agent.service.AgentSkillConfigService;
import io.github.mangomaner.mangobot.utils.FileUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.stream.Collectors;

/**
 * Skill 管理器
 * 
 * <p>负责管理 Skill 的生命周期：
 * <ul>
 *   <li>{@link #init()} - 启动时扫描并同步 Skill 目录</li>
 *   <li>{@link #createSkillDirectory(String)} - 创建 Skill 目录</li>
 *   <li>{@link #writeSkillContent(String, String)} - 写入 Skill 内容</li>
 *   <li>{@link #deleteSkillDirectory(String)} - 删除 Skill 目录</li>
 * </ul>
 *
 * <h3>Skill 目录结构</h3>
 * <p>目录本身就是交给 claude 的插件目录（{@code --plugin-dir}），技能必须落在它下面的
 * {@code skills/} 子目录里才会被识别——实测直接把技能目录挂在插件目录根下，claude 一个技能都发现不了。
 * <pre>
 * data/capabilities/skills/          # 插件目录（--plugin-dir 指向这里）
 * └── skills/
 *     └── my-skill/
 *         ├── SKILL.md           # Skill 主文件（必须）
 *         ├── references/        # 参考文档（可选）
 *         │   └── doc.md
 *         ├── examples/          # 示例文件（可选）
 *         │   └── example.md
 *         └── scripts/           # 脚本文件（可选）
 *             └── helper.sh
 * </pre>
 *
 * <h3>SKILL.md 格式</h3>
 * <pre>
 * ---
 * name: My Skill
 * description: A sample skill
 * bound_tool_ids: [1, 2, 3]
 * ---
 * 
 * Skill content here...
 * </pre>
 * 
 * @see AgentSkillConfig
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SkillManager {

    /** 插件目录：claude 的 {@code --plugin-dir} 指向这里（主库，能力页用） */
    private static final String PLUGIN_DIR = "data/capabilities/skills";

    /** 技能内容目录：插件目录下的 skills/ 子目录，每个技能一格 */
    private static final String SKILLS_DIR = PLUGIN_DIR + "/skills";

    /** 按来源生成的插件目录根：{@code data/capabilities/plugins/<sourceKey>/} */
    private static final String SOURCE_PLUGIN_ROOT = "data/capabilities/plugins";

    private final AgentSkillConfigService skillConfigService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 获取插件目录路径（claude {@code --plugin-dir} 的值）
     *
     * @return 插件目录路径
     */
    public Path getPluginDirectory() {
        return FileUtils.resolvePath(PLUGIN_DIR);
    }

    /**
     * 获取指定来源的插件目录——由 {@link #refreshSourcePluginDirs()} 按技能启用来源生成。
     *
     * <p>之所以按来源分目录而不是共用一个：claude 的 {@code --plugin-dir} 是整目录下发，
     * 一个目录里的技能会对所有会话可见，做不到"同一份技能库、群聊看得到的和 web 看得到的不一样"。
     *
     * @param source 会话来源
     * @return 该来源的插件目录路径（可能尚未生成，内容为空）
     */
    public Path getPluginDirectory(SessionSource source) {
        return FileUtils.resolvePath(SOURCE_PLUGIN_ROOT).resolve(source.getSourceKey());
    }

    /**
     * 获取 Skill 存储目录路径
     *
     * @return Skill 目录路径
     */
    public Path getSkillsDirectory() {
        return FileUtils.resolvePath(SKILLS_DIR);
    }

    /**
     * 初始化 Skill 同步
     * 
     * <p>扫描 Skill 目录，将文件系统中的 Skill 同步到数据库：
     * <ul>
     *   <li>新增的 Skill：写入数据库，默认禁用</li>
     *   <li>已存在的 Skill：更新元数据，保留启用状态</li>
     *   <li>数据库中存在但文件不存在的 Skill：保留配置（标记为不可用）</li>
     * </ul>
     */
    public void init() {
        FileUtils.createDirectory(getPluginDirectory());
        Path skillsDir = getSkillsDirectory();
        FileUtils.createDirectory(skillsDir);

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(skillsDir)) {
            for (Path skillPath : stream) {
                if (Files.isDirectory(skillPath)) {
                    Path skillMdPath = skillPath.resolve("SKILL.md");
                    if (Files.exists(skillMdPath)) {
                        String skillPathName = skillPath.getFileName().toString();
                        syncSkill(skillPathName, skillMdPath);
                    }
                }
            }
        } catch (IOException e) {
            log.error("Failed to scan skills directory", e);
        }

        refreshSourcePluginDirs();
    }

    /**
     * 按「技能启用来源」重建三个来源的插件目录（web / group / private）。
     *
     * <p>规则：主库里 {@code enabled == true} 且 {@code enabled_list} 含该来源的技能，整目录复制过去；
     * 三个目标目录先清空再复制。技能目录很小（就是些 markdown），重建比做增量diff简单得多。
     *
     * <p>调用时机是显式的——{@link #init()} 一次，加上能力页每一个改变技能状态的端点结尾。
     * 不做"惰性感知变更"：那要求在下发路径上比对 mtime，出错时很难查。
     */
    public void refreshSourcePluginDirs() {
        List<AgentSkillConfig> enabledSkills = skillConfigService.listEnabled();
        for (SessionSource source : SessionSource.values()) {
            Path target = getPluginDirectory(source);
            try {
                if (Files.exists(target)) {
                    deleteRecursively(target);
                }
                Path skillsDir = target.resolve("skills");
                FileUtils.createDirectory(skillsDir);

                int copied = 0;
                for (AgentSkillConfig skill : enabledSkills) {
                    if (!supportsSource(skill, source)) {
                        continue;
                    }
                    Path src = getSkillsDirectory().resolve(skill.getSkillPath());
                    if (!Files.isDirectory(src)) {
                        log.warn("技能「{}」已启用但目录不存在，跳过：{}", skill.getSkillPath(), src);
                        continue;
                    }
                    copyRecursively(src, skillsDir.resolve(skill.getSkillPath()));
                    copied++;
                }
                log.info("来源插件目录已重建：source={} dir={} skills={}",
                        source.getSourceKey(), target, copied);
            } catch (IOException e) {
                log.error("重建来源插件目录失败：source={} dir={}", source.getSourceKey(), target, e);
            }
        }
    }

    /** 技能对该来源是否启用：总开关打开，且 enabled_list 里有这个来源。 */
    private boolean supportsSource(AgentSkillConfig skill, SessionSource source) {
        if (!Boolean.TRUE.equals(skill.getEnabled())) {
            return false;
        }
        return parseSourceKeys(skill.getEnabledList()).contains(source.getSourceKey());
    }

    /** 解析 enabled_list；空或坏 JSON 一律按「没有来源」处理，不让一条坏配置拖垮整次重建。 */
    private List<String> parseSourceKeys(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (JsonProcessingException e) {
            log.warn("技能来源列表不是合法 JSON，按「无来源」处理：{}", json);
            return List.of();
        }
    }

    /** 整目录复制（技能目录含 references/ examples/ scripts/ 等资源，必须一起带过去）。 */
    private void copyRecursively(Path source, Path target) throws IOException {
        if (Files.isDirectory(source)) {
            FileUtils.createDirectory(target);
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(source)) {
                for (Path child : stream) {
                    copyRecursively(child, target.resolve(child.getFileName().toString()));
                }
            }
            return;
        }
        FileUtils.createParentDirectories(target);
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * 同步单个 Skill 到数据库
     */
    private void syncSkill(String skillPathName, Path skillMdPath) {
        try {
            String content = Files.readString(skillMdPath);
            SkillMetadata metadata = parseSkillMetadata(content);
            
            AgentSkillConfig config = skillConfigService.getBySkillPath(skillPathName);
            if (config == null) {
                config = newConfig(skillPathName, metadata);
            }

            config.setSkillName(metadata.name);
            config.setDescription(metadata.description);
            if (metadata.boundToolIds != null && !metadata.boundToolIds.isEmpty()) {
                config.setBoundToolIds(objectMapper.writeValueAsString(metadata.boundToolIds));
            }

            skillConfigService.saveOrUpdate(config);
            log.info("Skill synced: {}", skillPathName);

        } catch (IOException e) {
            log.error("Failed to read skill file: {}", skillMdPath, e);
        }
    }

    /**
     * 新建一条技能配置：默认支持所有来源，**声明了 {@code layer} 的内置技能默认启用**。
     *
     * <p>为什么系统层技能要默认开：persona 是 bot 行为的地基，新装环境里它要是关着的，bot 就没有人格
     * 也没有参与纪律（技能页上看起来一切正常）。这与 {@code registerBuiltinTool} 对新增内置工具的处理
     * 一致——只有新建这一次置 true；行一旦存在，用户在技能页的开关就说了算，同步不再碰它。
     */
    private AgentSkillConfig newConfig(String skillPathName, SkillMetadata metadata) {
        AgentSkillConfig config = new AgentSkillConfig();
        config.setSkillPath(skillPathName);
        config.setEnabled(metadata.layer != null && !metadata.layer.isBlank());
        String allSourcesJson = buildAllSourcesJson();
        config.setEnabledList(allSourcesJson);
        config.setAvailableList(allSourcesJson);
        return config;
    }

    /**
     * 导入 ZIP 格式的 Skill（zip 内需包含 SKILL.md，可附带 references/examples/scripts 资源）
     *
     * @param zipStream ZIP 输入流
     * @return 导入后的 Skill 配置（默认禁用，需手动启用）
     */
    public AgentSkillConfig importFromZip(InputStream zipStream) throws IOException {
        Path skillsRoot = getSkillsDirectory();
        FileUtils.createDirectory(skillsRoot);

        Map<String, byte[]> entries = new LinkedHashMap<>();
        long totalSize = 0;
        int entryCount = 0;

        try (ZipInputStream zis = new ZipInputStream(zipStream)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName().replace('\\', '/');
                if (name.startsWith("/") || name.contains("..") || name.matches("^[A-Za-z]:.*")) {
                    throw new IOException("ZIP 中包含非法路径: " + name);
                }
                if (++entryCount > 200) {
                    throw new IOException("ZIP 文件条目过多（>200）");
                }
                byte[] bytes = zis.readAllBytes();
                totalSize += bytes.length;
                if (totalSize > 10 * 1024 * 1024) {
                    throw new IOException("ZIP 文件过大（>10MB）");
                }
                entries.put(name, bytes);
            }
        }

        // 定位 SKILL.md（优先顶层目录形式 <name>/SKILL.md，其次任意层级）
        String skillMdEntry = entries.keySet().stream()
                .filter(name -> name.endsWith("/SKILL.md"))
                .findFirst()
                .orElseGet(() -> entries.keySet().stream()
                        .filter(name -> name.endsWith("SKILL.md"))
                        .findFirst()
                        .orElse(null));
        if (skillMdEntry == null) {
            throw new IOException("ZIP 中未找到 SKILL.md");
        }

        String parent = skillMdEntry.contains("/")
                ? skillMdEntry.substring(0, skillMdEntry.lastIndexOf('/'))
                : "";
        String skillPath = parent;

        if (skillPath == null || skillPath.isEmpty()) {
            // SKILL.md 在 zip 根目录：从前置元数据解析名称作为路径
            String mdContent = new String(entries.get(skillMdEntry), StandardCharsets.UTF_8);
            SkillMetadata metadata = parseSkillMetadata(mdContent);
            skillPath = sanitizePath(metadata.name);
            if (skillPath == null || skillPath.isEmpty()) {
                throw new IOException("SKILL.md 缺少 name 元数据，无法确定技能路径");
            }
        }

        Path skillDir = skillsRoot.resolve(skillPath);
        for (Map.Entry<String, byte[]> fileEntry : entries.entrySet()) {
            String relative = fileEntry.getKey();
            if (skillMdEntry.contains("/") && relative.startsWith(parent + "/")) {
                relative = relative.substring(parent.length() + 1);
            }
            if (relative.isEmpty()) {
                continue;
            }
            Path target = skillDir.resolve(relative).normalize();
            if (!target.startsWith(skillDir)) {
                throw new IOException("ZIP 中包含越界路径: " + fileEntry.getKey());
            }
            FileUtils.createParentDirectories(target);
            Files.write(target, fileEntry.getValue());
        }

        log.info("Skill imported from zip: {}", skillPath);
        return syncSkillByPath(skillPath);
    }

    /**
     * 导入单文件 Skill（SKILL.md 内容）
     *
     * @param skillName   技能名称
     * @param description 技能描述（content 自带 frontmatter 时忽略）
     * @param content     SKILL.md 内容
     * @return 导入后的 Skill 配置（默认禁用）
     */
    public AgentSkillConfig importFromMarkdown(String skillName, String description, String content) throws IOException {
        String skillPath = sanitizePath(skillName);
        if (skillPath == null || skillPath.isEmpty()) {
            throw new IOException("技能名称不能为空或非法");
        }

        String fullContent = content;
        if (!content.trim().startsWith("---")) {
            fullContent = "---\n"
                    + "name: " + skillName + "\n"
                    + "description: " + (description != null ? description : "") + "\n"
                    + "---\n\n"
                    + content;
        }

        Path skillDir = getSkillsDirectory().resolve(skillPath);
        FileUtils.createDirectory(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), fullContent);

        log.info("Skill imported from markdown: {}", skillPath);
        return syncSkillByPath(skillPath);
    }

    /**
     * 按路径同步单个 Skill 到数据库（读取 SKILL.md，解析元数据，更新配置）
     */
    public AgentSkillConfig syncSkillByPath(String skillPathName) throws IOException {
        Path skillMdPath = getSkillsDirectory().resolve(skillPathName).resolve("SKILL.md");
        if (!Files.exists(skillMdPath)) {
            throw new IOException("SKILL.md not found: " + skillMdPath);
        }
        String content = Files.readString(skillMdPath);
        SkillMetadata metadata = parseSkillMetadata(content);

        AgentSkillConfig config = skillConfigService.getBySkillPath(skillPathName);
        if (config == null) {
            config = newConfig(skillPathName, metadata);
        }

        config.setSkillName(metadata.name != null ? metadata.name : skillPathName);
        config.setDescription(metadata.description);
        if (metadata.boundToolIds != null && !metadata.boundToolIds.isEmpty()) {
            config.setBoundToolIds(objectMapper.writeValueAsString(metadata.boundToolIds));
        }
        skillConfigService.saveOrUpdate(config);
        log.info("Skill synced: {}", skillPathName);
        return config;
    }

    /**
     * 解析 SKILL.md 的 YAML frontmatter
     */
    @SuppressWarnings("unchecked")
    private SkillMetadata parseSkillMetadata(String content) {
        SkillMetadata metadata = new SkillMetadata();
        if (content.startsWith("---")) {
            int endIndex = content.indexOf("---", 3);
            if (endIndex > 0) {
                String frontmatter = content.substring(3, endIndex);
                Yaml yaml = new Yaml();
                Map<String, Object> data = yaml.load(frontmatter);
                Object nameObj = data.get("name");
                if (nameObj != null) {
                    metadata.name = String.valueOf(nameObj);
                }
                Object descObj = data.get("description");
                if (descObj != null) {
                    metadata.description = String.valueOf(descObj);
                }
                Object boundToolsObj = data.get("bound_tool_ids");
                if (boundToolsObj instanceof List) {
                    List<?> list = (List<?>) boundToolsObj;
                    metadata.boundToolIds = list.stream()
                            .map(obj -> {
                                if (obj instanceof Number) {
                                    return ((Number) obj).intValue();
                                }
                                return Integer.parseInt(String.valueOf(obj));
                            })
                            .collect(Collectors.toList());
                }
                Object layerObj = data.get("layer");
                if (layerObj != null) {
                    metadata.layer = String.valueOf(layerObj).trim();
                }
            }
        }
        return metadata;
    }

    /**
     * 创建 Skill 目录
     * 
     * @param skillPath Skill 路径（相对于 skills 目录）
     */
    public void createSkillDirectory(String skillPath) {
        Path skillDir = getSkillsDirectory().resolve(skillPath);
        FileUtils.createDirectory(skillDir);
    }

    /**
     * 写入 Skill 内容
     * 
     * @param skillPath Skill 路径
     * @param content SKILL.md 内容
     */
    public void writeSkillContent(String skillPath, String content) throws IOException {
        Path skillMdPath = getSkillsDirectory().resolve(skillPath).resolve("SKILL.md");
        FileUtils.createParentDirectories(skillMdPath);
        Files.writeString(skillMdPath, content);
    }

    /**
     * 读取 Skill 内容
     * 
     * @param skillPath Skill 路径
     * @return SKILL.md 内容（不存在返回 null）
     */
    public String readSkillContent(String skillPath) throws IOException {
        Path skillMdPath = getSkillsDirectory().resolve(skillPath).resolve("SKILL.md");
        if (!Files.exists(skillMdPath)) {
            return null;
        }
        return Files.readString(skillMdPath);
    }

    /**
     * 删除 Skill 目录
     * 
     * @param skillPath Skill 路径
     */
    public void deleteSkillDirectory(String skillPath) throws IOException {
        Path skillDir = getSkillsDirectory().resolve(skillPath);
        if (Files.exists(skillDir)) {
            deleteRecursively(skillDir);
        }
    }

    /**
     * 递归删除目录
     */
    private void deleteRecursively(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
                for (Path child : stream) {
                    deleteRecursively(child);
                }
            }
        }
        Files.delete(path);
    }

    /** Skill 元数据 */
    private static class SkillMetadata {
        String name;
        String description;
        List<Integer> boundToolIds;
        /** frontmatter 里的 {@code layer}：非空表示这是随项目内置的技能（如 persona / slang），默认启用 */
        String layer;
    }

    /**
     * 构建包含所有来源的 JSON 字符串
     *
     * @return 所有 SessionSource 的 JSON 数组字符串
     */
    private String buildAllSourcesJson() {
        List<String> allSourceKeys = Arrays.stream(SessionSource.values())
                .map(SessionSource::getSourceKey)
                .collect(Collectors.toList());
        try {
            return objectMapper.writeValueAsString(allSourceKeys);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize all sources, using hardcoded default", e);
            return "[\"web\",\"group\",\"private\"]";
        }
    }

    /**
     * 将技能名规整为文件系统安全路径段（小写，非字母数字统一转 -）
     */
    public static String sanitizePath(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        return name.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_-]+", "-")
                .replaceAll("^-+|-+$", "");
    }
}
