package io.github.mangomaner.mangobot.module.agent.capability.skill;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mangomaner.mangobot.infra.claude.skill.ClaudeSkillRegistry;
import io.github.mangomaner.mangobot.infra.claude.skill.ClaudeSkillScope;
import io.github.mangomaner.mangobot.module.agent.model.domain.AgentSkillConfig;
import io.github.mangomaner.mangobot.module.agent.model.enums.SessionSource;
import io.github.mangomaner.mangobot.module.agent.model.vo.SkillFileNodeVO;
import io.github.mangomaner.mangobot.module.agent.service.AgentSkillConfigService;
import io.github.mangomaner.mangobot.utils.FileUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Skill 管理器：技能库的读写 + 「作用域 → 技能清单」这张表。
 *
 * <h3>两个库，类型由目录决定</h3>
 * <pre>
 * data/capabilities/
 * ├── all-skills/&lt;名&gt;/skills/&lt;名&gt;/SKILL.md      # 普通技能（按来源启用）
 * └── all-personas/&lt;名&gt;/skills/&lt;名&gt;/SKILL.md    # 人格（按作用域指派）
 * </pre>
 * 路径拼接全在 {@link SkillLibrary}。这里不再有 {@code is_persona} 标记位，也不再按作用域复制目录
 * ——{@code --plugin-dir} 可以重复传，所以"这个会话能看到哪些技能"是 {@link ClaudeSkillRegistry}
 * 里的一张表，不是磁盘上的一份副本。
 *
 * <h3>清单怎么算</h3>
 * <ul>
 *   <li>普通技能：只看来源（{@code enabled} + {@code enabled_list} 含该来源）。</li>
 *   <li>人格：指派到哪个作用域（全局 / bot / 会话）就进哪一份清单，每个作用域至多一个。</li>
 *   <li>每份清单 = 该来源的全部普通技能 + 该作用域生效的那个人格。回退是"取第一个有表项的"而非合并，
 *       所以<b>清单必须自包含</b>；同理，只在该级真有人格时才建 bot / 会话级表项，否则会遮蔽掉全局人格。</li>
 * </ul>
 *
 * <h3>SKILL.md 格式</h3>
 * <pre>
 * ---
 * name: My Skill
 * description: A sample skill
 * bound_tool_ids: [1, 2, 3]
 * layer: system          # 非空表示随项目内置，新建时默认启用
 * ---
 * </pre>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SkillManager {

    /** 旧布局（迁移用）：插件目录，其下 {@code skills/<名>/} 是技能库。 */
    private static final String LEGACY_PLUGIN_DIR = "data/capabilities/skills";

    /** 旧布局（迁移用）：按来源复制的副本树。 */
    private static final String LEGACY_SOURCE_PLUGIN_ROOT = "data/capabilities/plugins";

    private final AgentSkillConfigService skillConfigService;
    private final ClaudeSkillRegistry skillRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ========================================================================
    // 启动：迁移 + 扫描 + 建表
    // ========================================================================

    /**
     * 初始化：先搬家（一次性），再扫描两个库同步元数据，最后重建作用域清单。
     * 由 {@code MangoBotApplication.main()} 显式调用。
     */
    public void init() {
        migrateLegacyLayout();
        for (String root : SkillLibrary.roots()) {
            Path rootPath = FileUtils.resolvePath(root);
            FileUtils.createDirectory(rootPath);
            scanRoot(root, rootPath);
        }
        refreshSkillScopes();
    }

    /** 扫一个库：{@code <根>/<名>/skills/<名>/SKILL.md} 才是合法技能。 */
    private void scanRoot(String root, Path rootPath) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(rootPath)) {
            for (Path child : stream) {
                if (!Files.isDirectory(child)) {
                    continue;
                }
                String name = child.getFileName().toString();
                Path skillMd = child.resolve(SkillLibrary.SKILLS_SUBDIR).resolve(name).resolve("SKILL.md");
                if (Files.exists(skillMd)) {
                    syncSkill(SkillLibrary.pathOf(SkillLibrary.kindOf(root + "/" + name), name), skillMd);
                } else if (Files.exists(child.resolve("SKILL.md"))) {
                    log.warn("技能目录形状不对，claude 发现不了它（缺 {}/{} 这一层）：{}",
                            SkillLibrary.SKILLS_SUBDIR, name, child);
                }
            }
        } catch (IOException e) {
            log.error("扫描技能库失败：{}", rootPath, e);
        }
    }

    /**
     * 一次性搬家：旧的单库布局 → 双库布局，并清掉按来源复制的副本树。
     *
     * <p>数据库侧的路径改写已经在 {@code SkillSchema} 做过（那边才读得到旧的 {@code is_persona}），
     * 所以这里按<b>新路径</b>把目录搬到位。幂等：旧的技能库目录搬完就删了，第二次启动直接跳过。
     */
    private void migrateLegacyLayout() {
        Path legacyPluginDir = FileUtils.resolvePath(LEGACY_PLUGIN_DIR);
        Path legacySkills = legacyPluginDir.resolve(SkillLibrary.SKILLS_SUBDIR);
        try {
            if (Files.isDirectory(legacySkills)) {
                int moved = 0;
                // 旧技能库里，凡是数据库里有行的都已经按新路径落地；记下来，免得被下面的孤儿分支
                // 又当成"没主的目录"复制进技能库（人格的目标在人格库，用目标路径判断根本判不出来）
                Set<String> known = new HashSet<>();
                for (AgentSkillConfig config : skillConfigService.list()) {
                    String name = SkillLibrary.nameOf(config.getSkillPath());
                    known.add(name);
                    Path source = legacySkills.resolve(name);
                    if (!Files.isDirectory(source)) {
                        continue;
                    }
                    Path target = SkillLibrary.contentDir(config.getSkillPath());
                    if (Files.exists(target)) {
                        continue;
                    }
                    copyRecursively(source, target);
                    moved++;
                }
                // 数据库里确实没有的目录：一律按普通技能收进技能库，不丢内容
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(legacySkills)) {
                    for (Path child : stream) {
                        String name = child.getFileName().toString();
                        if (!Files.isDirectory(child) || known.contains(name)) {
                            continue;
                        }
                        Path target = SkillLibrary.contentDirOf(SkillLibrary.Kind.SKILL, name);
                        if (Files.exists(target)) {
                            continue;
                        }
                        copyRecursively(child, target);
                        moved++;
                        log.warn("技能「{}」不在数据库里，已按普通技能收进技能库，请到技能页确认", name);
                    }
                }
                log.info("旧技能库已搬到双库布局，共 {} 个技能；旧目录删除中", moved);
            }
        } catch (IOException e) {
            log.error("技能库搬迁失败，本次不给技能建清单（避免下发到半搬完的目录）", e);
            return;
        }
        deleteQuietly(legacyPluginDir);
        deleteQuietly(FileUtils.resolvePath(LEGACY_SOURCE_PLUGIN_ROOT));
    }

    // ========================================================================
    // 作用域 → 技能清单
    // ========================================================================

    /**
     * 重建整张作用域清单表并<b>原子发布</b>。
     *
     * <p>调用时机是显式的：启动一次，加上每一个改变"技能启用状态 / 人格指派"的端点。
     * 改技能<b>正文</b>不用调——正文就是进程下次启动时读的那一份，没有副本要同步。
     */
    public void refreshSkillScopes() {
        List<AgentSkillConfig> all = skillConfigService.list();
        List<AgentSkillConfig> generals = all.stream()
                .filter(config -> SkillLibrary.kindOf(config.getSkillPath()) == SkillLibrary.Kind.SKILL)
                .filter(config -> Boolean.TRUE.equals(config.getEnabled()))
                .toList();
        List<AgentSkillConfig> personas = all.stream()
                .filter(config -> SkillLibrary.kindOf(config.getSkillPath()) == SkillLibrary.Kind.PERSONA)
                .filter(config -> Boolean.TRUE.equals(config.getEnabled()))
                .toList();

        Map<ClaudeSkillScope, List<Path>> table = new LinkedHashMap<>();
        for (SessionSource source : SessionSource.values()) {
            List<AgentSkillConfig> forSource = generals.stream()
                    .filter(skill -> sourcesOf(skill).contains(source))
                    .toList();
            // 全局那一份恒有表项（可能只有普通技能、没有人格）
            PersonaScope globalScope = PersonaScope.global(source);
            table.put(globalScope.toSkillScope(), compose(forSource, personaAt(globalScope, personas)));
            // bot / 会话：只在该级真有人格时才建表项。建了就会遮蔽全局人格，所以不能提前建。
            for (AgentSkillConfig persona : personas) {
                for (String key : parseKeys(persona.getEnabledList())) {
                    PersonaScope scope = tryParseScope(key);
                    if (scope == null || scope.source() != source || scope.level() == PersonaScope.Level.GLOBAL) {
                        continue;
                    }
                    table.put(scope.toSkillScope(), compose(forSource, persona));
                }
            }
        }
        skillRegistry.replaceAll(table);
        logScopeTable(table);
    }

    /** 一份清单 = 该来源的全部普通技能 + 该作用域生效的那个人格。 */
    private List<Path> compose(List<AgentSkillConfig> generals, AgentSkillConfig persona) {
        String personaName = persona == null ? null : SkillLibrary.nameOf(persona.getSkillPath());
        List<Path> dirs = new ArrayList<>();
        for (AgentSkillConfig general : generals) {
            String name = SkillLibrary.nameOf(general.getSkillPath());
            if (name.equals(personaName)) {
                // 插件名取目录名，两者同名时模型侧的名字完全一样（都是 <名>:<名>），无法区分，
                // 所以这份清单里只留人格。改名即可解决。
                log.warn("技能「{}」与生效的人格同名，模型侧无法区分，本次只下发人格；请把其中一个改名",
                        general.getSkillPath());
                continue;
            }
            dirs.add(SkillLibrary.pluginRoot(general.getSkillPath()));
        }
        if (persona != null) {
            dirs.add(SkillLibrary.pluginRoot(persona.getSkillPath()));
        }
        return dirs;
    }

    private AgentSkillConfig personaAt(PersonaScope scope, List<AgentSkillConfig> personas) {
        for (AgentSkillConfig persona : personas) {
            if (parseKeys(persona.getEnabledList()).contains(scope.key())) {
                return persona;
            }
        }
        return null;
    }

    private void logScopeTable(Map<ClaudeSkillScope, List<Path>> table) {
        table.forEach((scope, dirs) -> log.info("技能清单：{} {} → {}",
                scope.level(), scope.scopeId(),
                dirs.isEmpty() ? "（空）" : dirs.stream().map(Path::getFileName).toList()));
    }

    /**
     * 这个技能对该来源现在启用吗。
     *
     * <p>给"技能变更之后该通知哪些会话"用。判定必须认<b>两种键</b>：普通技能存来源键 {@code group}，
     * 人格存作用域键 {@code 1389:group} / {@code 1389:group:67890}——只比字符串相等的话，
     * 人格的键一个都匹配不上，改人格正文时模型收不到重读指令。
     */
    public boolean isEnabledFor(String skillPath, SessionSource source) {
        AgentSkillConfig config = skillConfigService.getBySkillPath(skillPath);
        if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
            return false;
        }
        for (String key : parseKeys(config.getEnabledList())) {
            PersonaScope scope = tryParseScope(key);
            if (scope != null && scope.source() == source) {
                return true;
            }
        }
        return false;
    }

    /** 技能启用列表里的来源（普通技能的键就是来源键）。 */
    private List<SessionSource> sourcesOf(AgentSkillConfig skill) {
        List<SessionSource> sources = new ArrayList<>();
        for (String key : parseKeys(skill.getEnabledList())) {
            PersonaScope scope = tryParseScope(key);
            if (scope != null && scope.level() == PersonaScope.Level.GLOBAL) {
                sources.add(scope.source());
            }
        }
        return sources;
    }

    // ========================================================================
    // 人格指派
    // ========================================================================

    /**
     * 把人某作用域的「当前人格」设为指定技能：先把该作用域键从<b>所有</b>人格里摘掉，再挂到指定技能上。
     * 于是任何作用域任一时刻至多命中一个人格。
     *
     * <p>只摘<b>同一个作用域键</b>，不是整行——给某个群换人格不该动到同一个人格在 bot 级或全局级的指派。
     * 加锁是因为一次指派是"读全部人格行 → 逐行写"，得让"同级至多一个"这条不变式有个单点保证。
     *
     * @param personaPath 目标人格路径；传 null 表示取消该作用域的人格
     */
    public synchronized void assignPersona(String personaPath, PersonaScope scope) throws JsonProcessingException {
        String key = scope.key();
        for (AgentSkillConfig persona : skillConfigService.listPersonas()) {
            List<String> keys = new ArrayList<>(parseKeys(persona.getEnabledList()));
            boolean changed = keys.remove(key);
            boolean isTarget = personaPath != null && personaPath.equals(persona.getSkillPath());
            if (isTarget && !keys.contains(key)) {
                keys.add(key);
                changed = true;
            } else if (!isTarget && !changed) {
                continue; // 无关的行，不用写库
            }
            persona.setEnabledList(objectMapper.writeValueAsString(keys));
            persona.setEnabled(!keys.isEmpty());
            skillConfigService.updateById(persona);
        }
    }

    /** 某个作用域当前命中的那个「唯一人格」的 skill_path，没有则返回 null。 */
    public String activePersonaPathFor(PersonaScope scope) {
        String key = scope.key();
        for (AgentSkillConfig persona : skillConfigService.listPersonas()) {
            if (Boolean.TRUE.equals(persona.getEnabled())
                    && parseKeys(persona.getEnabledList()).contains(key)) {
                return persona.getSkillPath();
            }
        }
        return null;
    }

    /** 全部指派，供人格页回填。 */
    public List<PersonaAssignment> listPersonaAssignments() {
        List<PersonaAssignment> assignments = new ArrayList<>();
        for (AgentSkillConfig persona : skillConfigService.listPersonas()) {
            for (String key : parseKeys(persona.getEnabledList())) {
                PersonaScope scope = tryParseScope(key);
                if (scope != null) {
                    assignments.add(new PersonaAssignment(scope, persona.getSkillPath()));
                }
            }
        }
        return assignments;
    }

    // ========================================================================
    // 导入 / 导出 / 同步
    // ========================================================================

    /**
     * 导入 ZIP 格式的技能（zip 内需包含 SKILL.md，可附带 references/examples/scripts 资源）。
     *
     * @param kind 落哪个库：技能页导入的是 {@code SKILL}，人格页是 {@code PERSONA}
     * @return 导入后的技能配置（默认禁用，需手动启用/指派）
     */
    public AgentSkillConfig importFromZip(InputStream zipStream, SkillLibrary.Kind kind) throws IOException {
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
        String skillName = parent;
        if (skillName.isEmpty()) {
            // SKILL.md 在 zip 根目录：从前置元数据解析名称作为路径
            String mdContent = new String(entries.get(skillMdEntry), StandardCharsets.UTF_8);
            skillName = sanitizePath(parseSkillMetadata(mdContent).name);
        }
        if (skillName == null || skillName.isEmpty()) {
            throw new IOException("SKILL.md 缺少 name 元数据，无法确定技能名");
        }

        String skillPath = SkillLibrary.pathOf(kind, skillName);
        Path contentDir = SkillLibrary.contentDir(skillPath);
        for (Map.Entry<String, byte[]> fileEntry : entries.entrySet()) {
            String relative = fileEntry.getKey();
            if (!parent.isEmpty() && relative.startsWith(parent + "/")) {
                relative = relative.substring(parent.length() + 1);
            }
            if (relative.isEmpty()) {
                continue;
            }
            Path target = FileUtils.resolvePath(contentDir, relative);
            FileUtils.createParentDirectories(target);
            Files.write(target, fileEntry.getValue());
        }

        log.info("Skill imported from zip: {}", skillPath);
        return syncSkillByPath(skillPath);
    }

    /**
     * 导入单文件 Skill（SKILL.md 内容）。
     *
     * @param kind 落哪个库；技能页是 {@code SKILL}，人格页是 {@code PERSONA}
     */
    public AgentSkillConfig importFromMarkdown(String skillName, String description, String content,
            SkillLibrary.Kind kind) throws IOException {
        String name = sanitizePath(skillName);
        if (name == null || name.isEmpty()) {
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

        String skillPath = SkillLibrary.pathOf(kind, name);
        Path skillMdPath = SkillLibrary.skillMd(skillPath);
        FileUtils.createParentDirectories(skillMdPath);
        Files.writeString(skillMdPath, fullContent);

        log.info("Skill imported from markdown: {}", skillPath);
        return syncSkillByPath(skillPath);
    }

    /**
     * 导出 Skill 目录为 ZIP 包（技能页与人格页共用）。
     *
     * <p>zip 内以技能目录名作顶层目录，形如 {@code <名>/SKILL.md}、{@code <名>/references/doc.md}
     * ——与 {@link #importFromZip} 的解析正好对得上：它以 {@code SKILL.md} 所在目录为名还原技能，
     * 所以导出的包可被导入功能原样还原到同一路径（round-trip）。
     */
    public byte[] exportSkillZip(String skillPath) throws IOException {
        Path contentDir = resolveSkillDir(skillPath);
        String baseName = contentDir.getFileName().toString();
        List<Path> files;
        try (var stream = Files.walk(contentDir)) {
            files = stream.filter(Files::isRegularFile).collect(Collectors.toList());
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            for (Path file : files) {
                String rel = contentDir.relativize(file).toString().replace('\\', '/');
                zos.putNextEntry(new ZipEntry(baseName + "/" + rel));
                Files.copy(file, zos);
                zos.closeEntry();
            }
        }
        log.info("Skill exported to zip: {}", skillPath);
        return bos.toByteArray();
    }

    /** 按路径同步单个 Skill 到数据库（读取 SKILL.md，解析元数据，更新配置）。 */
    public AgentSkillConfig syncSkillByPath(String skillPath) throws IOException {
        Path skillMdPath = SkillLibrary.skillMd(skillPath);
        if (!Files.exists(skillMdPath)) {
            throw new IOException("SKILL.md not found: " + skillMdPath);
        }
        String content = Files.readString(skillMdPath);
        SkillMetadata metadata = parseSkillMetadata(content);

        AgentSkillConfig config = skillConfigService.getBySkillPath(skillPath);
        if (config == null) {
            config = newConfig(skillPath, metadata);
        }
        config.setSkillName(metadata.name != null ? metadata.name : SkillLibrary.nameOf(skillPath));
        config.setDescription(metadata.description);
        if (metadata.boundToolIds != null && !metadata.boundToolIds.isEmpty()) {
            config.setBoundToolIds(objectMapper.writeValueAsString(metadata.boundToolIds));
        }
        skillConfigService.saveOrUpdate(config);
        log.info("Skill synced: {}", skillPath);
        return config;
    }

    /** 扫库同步（保留行上的启用状态，只更新元数据）。 */
    private void syncSkill(String skillPath, Path skillMdPath) {
        try {
            String content = Files.readString(skillMdPath);
            SkillMetadata metadata = parseSkillMetadata(content);

            AgentSkillConfig config = skillConfigService.getBySkillPath(skillPath);
            if (config == null) {
                config = newConfig(skillPath, metadata);
            }
            config.setSkillName(metadata.name);
            config.setDescription(metadata.description);
            if (metadata.boundToolIds != null && !metadata.boundToolIds.isEmpty()) {
                config.setBoundToolIds(objectMapper.writeValueAsString(metadata.boundToolIds));
            }
            skillConfigService.saveOrUpdate(config);
            log.info("Skill synced: {}", skillPath);
        } catch (IOException e) {
            log.error("Failed to read skill file: {}", skillMdPath, e);
        }
    }

    /**
     * 新建一条技能配置：默认支持所有来源，<b>声明了 {@code layer} 的内置技能默认启用</b>。
     *
     * <p>为什么系统层技能要默认开：persona 是 bot 行为的地基，新装环境里它要是关着的，bot 就没有人格
     * 也没有参与纪律（技能页上看起来一切正常）。这与 {@code registerBuiltinTool} 对新增内置工具的处理
     * 一致——只有新建这一次置 true；行一旦存在，用户在技能页的开关就说了算，同步不再碰它。
     *
     * <p>人格的 {@code enabled_list} 留空：它的"启用"由指派决定。若这里也填上全部来源，
     * 一旦 {@code enabled} 打开就会被读成"三个场景全局都已指派"。
     */
    private AgentSkillConfig newConfig(String skillPath, SkillMetadata metadata) {
        AgentSkillConfig config = new AgentSkillConfig();
        config.setSkillPath(skillPath);
        config.setEnabled(metadata.layer != null && !metadata.layer.isBlank());
        String allSourcesJson = buildAllSourcesJson();
        config.setEnabledList(SkillLibrary.kindOf(skillPath) == SkillLibrary.Kind.PERSONA
                ? "[]" : allSourcesJson);
        config.setAvailableList(allSourcesJson);
        return config;
    }

    /** 解析 SKILL.md 的 YAML frontmatter。 */
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

    // ========================================================================
    // 技能目录文件树编辑（技能页与人格页共用的文件编辑器）
    // ========================================================================

    /** 创建技能目录（正文目录那一层）。 */
    public void createSkillDirectory(String skillPath) {
        FileUtils.createDirectory(SkillLibrary.contentDir(skillPath));
    }

    /** 写入 SKILL.md。 */
    public void writeSkillContent(String skillPath, String content) throws IOException {
        Path skillMdPath = SkillLibrary.skillMd(skillPath);
        FileUtils.createParentDirectories(skillMdPath);
        Files.writeString(skillMdPath, content);
    }

    /** 读取 SKILL.md（不存在返回 null）。 */
    public String readSkillContent(String skillPath) throws IOException {
        Path skillMdPath = SkillLibrary.skillMd(skillPath);
        if (!Files.exists(skillMdPath)) {
            return null;
        }
        return Files.readString(skillMdPath);
    }

    /**
     * 列出 skill 目录的文件树。
     *
     * <p>只下发目录结构与节点名，<b>不</b>带叶子内容（references/ 里可能有较大文件），
     * 内容由前端选中单个文件时按需读取。目录在前的排序便于 n-tree 直接渲染。
     */
    public SkillFileNodeVO listFileTree(String skillPath) throws IOException {
        Path contentDir = resolveSkillDir(skillPath);
        return buildFileTree(contentDir, "", contentDir.getFileName().toString());
    }

    /** 递归构建文件树：目录在前、名字升序。 */
    private SkillFileNodeVO buildFileTree(Path dir, String relPrefix, String nodeName) throws IOException {
        SkillFileNodeVO node = new SkillFileNodeVO();
        node.setName(nodeName);
        node.setPath(relPrefix.isEmpty() ? "" : relPrefix);
        node.setDirectory(true);

        List<Path> children = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path child : stream) {
                children.add(child);
            }
        }
        // 目录在前，同级按文件名升序。
        children.sort(Comparator.comparing((Path p) -> Files.isDirectory(p)).thenComparing(Path::getFileName));
        for (Path child : children) {
            boolean isDir = Files.isDirectory(child);
            String name = child.getFileName().toString();
            String childPath = relPrefix.isEmpty() ? name : relPrefix + "/" + name;
            if (isDir) {
                node.getChildren().add(buildFileTree(child, childPath, name));
            } else {
                SkillFileNodeVO leaf = new SkillFileNodeVO();
                leaf.setName(name);
                leaf.setPath(childPath);
                leaf.setDirectory(false);
                node.getChildren().add(leaf);
            }
        }
        return node;
    }

    /** 读取 skill 目录下的指定文件内容（不存在返回 null）。 */
    public String readSkillFile(String skillPath, String relPath) throws IOException {
        Path file = resolveSkillFile(skillPath, relPath);
        if (!Files.exists(file) || Files.isDirectory(file)) {
            return null;
        }
        return Files.readString(file);
    }

    /**
     * 按「全量快照」保存 skill 目录：前端一次性把所有文件（路径→内容）发过来，
     * 这里先清空原目录再整份重建。新建/改名/删除在前端都是对这份快照的增删，
     * 落到后端只差一次「写回」，天然覆盖改文件名（旧 key 没了、新 key 有了）。
     */
    public void saveSkillFiles(String skillPath, Map<String, String> files) throws IOException {
        Path contentDir = resolveSkillDir(skillPath);
        if (!files.containsKey("SKILL.md")) {
            throw new IllegalArgumentException("SKILL.md 是 skill 的元数据文件，不能删除");
        }
        // 先写 SKILL.md，避免中途被别人读到半成品；文件原本就算不存在也会被 createParentDirectories 兜底。
        writeSkillFile(contentDir, "SKILL.md", files.get("SKILL.md"));
        for (Map.Entry<String, String> entry : files.entrySet()) {
            if ("SKILL.md".equals(entry.getKey())) {
                continue;
            }
            writeSkillFile(contentDir, entry.getKey(), entry.getValue());
        }
        // 快照里没有的文件/目录一并清掉（改名的旧路径、删除的文件都在这里）。
        // 先写新文件、后清旧文件，失败点尽量留在"写"这一步，旧目录被误清的风险最小。
        deleteUnlisted(contentDir, "", files.keySet());
    }

    /** 在 skill 目录下写一个文件（空内容也建，父目录自动补）。 */
    private void writeSkillFile(Path contentDir, String relPath, String content) throws IOException {
        Path target = resolveSkillFile(contentDir, relPath);
        FileUtils.createParentDirectories(target);
        Files.writeString(target, content == null ? "" : content);
    }

    /** 删除快照里没有列出的文件/目录（保留快照中存在的）。keepSet 是相对根目录的全量完整路径。 */
    private void deleteUnlisted(Path dir, String relPrefix, Set<String> keepSet) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path child : stream) {
                String name = child.getFileName().toString();
                String fullPath = relPrefix.isEmpty() ? name : relPrefix + "/" + name;
                if (Files.isDirectory(child)) {
                    // 目录本身在快照里永远不是 key；只要下面还有文件命中就保留并深入，否则整棵删掉。
                    boolean keepAny = keepSet.stream().anyMatch(k -> k.startsWith(fullPath + "/"));
                    if (keepAny) {
                        deleteUnlisted(child, fullPath, keepSet);
                    } else {
                        deleteRecursively(child);
                    }
                } else if (!keepSet.contains(fullPath)) {
                    Files.delete(child);
                }
            }
        }
    }

    /** 把相对路径安全地解析到某个 skill 目录下（防穿越，见 FileUtils.resolvePath）。 */
    private Path resolveSkillFile(String skillPath, String relPath) throws IOException {
        return resolveSkillFile(resolveSkillDir(skillPath), relPath);
    }

    private Path resolveSkillFile(Path contentDir, String relPath) {
        // 空路径或 . 视为 skill 根目录，不让读/写整个目录为文件。
        if (relPath == null || relPath.isBlank() || relPath.equals(".")) {
            throw new SecurityException("非法文件路径: " + relPath);
        }
        return FileUtils.resolvePath(contentDir, relPath);
    }

    /** 正文目录存在性校验并解析。 */
    private Path resolveSkillDir(String skillPath) throws IOException {
        if (skillPath == null || skillPath.isBlank()) {
            throw new IllegalArgumentException("skill 路径不能为空");
        }
        Path contentDir = SkillLibrary.contentDir(skillPath);
        if (!Files.isDirectory(contentDir)) {
            throw new IOException("skill 目录不存在: " + contentDir);
        }
        return contentDir;
    }

    /** 删除技能：整个插件根一起删（{@code <名>/} 下面只有这一套）。 */
    public void deleteSkillDirectory(String skillPath) throws IOException {
        Path pluginRoot = SkillLibrary.pluginRoot(skillPath);
        if (Files.exists(pluginRoot)) {
            deleteRecursively(pluginRoot);
        }
    }

    // ========================================================================
    // 内部工具
    // ========================================================================

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

    private void deleteQuietly(Path path) {
        try {
            if (Files.exists(path)) {
                deleteRecursively(path);
                log.info("已清理旧目录：{}", path);
            }
        } catch (IOException e) {
            log.warn("旧目录清理失败（不影响运行，可手动删）：{}", path, e);
        }
    }

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

    /** 解析启用列表；空或坏 JSON 一律按空处理，不让一条坏配置拖垮整次重建。 */
    private List<String> parseKeys(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (JsonProcessingException e) {
            log.warn("技能启用列表不是合法 JSON，按空处理：{}", json);
            return List.of();
        }
    }

    /** 解析作用域键；认不出（既不是来源键也不是作用域键）返回 null。 */
    private PersonaScope tryParseScope(String key) {
        try {
            return PersonaScope.parse(key);
        } catch (IllegalArgumentException e) {
            log.warn("认不出的作用域键，已跳过：{}", key);
            return null;
        }
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

    /** Skill 元数据 */
    private static class SkillMetadata {
        String name;
        String description;
        List<Integer> boundToolIds;
        /** frontmatter 里的 {@code layer}：非空表示这是随项目内置的技能（如 persona / slang），默认启用 */
        String layer;
    }
}
