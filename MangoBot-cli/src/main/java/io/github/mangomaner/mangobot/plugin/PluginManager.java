package io.github.mangomaner.mangobot.plugin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.mangomaner.mangobot.annotation.PluginConfig;
import io.github.mangomaner.mangobot.annotation.PluginConfigs;
import io.github.mangomaner.mangobot.annotation.PluginDescribe;
import io.github.mangomaner.mangobot.annotation.web.MangoBotRequestMapping;
import io.github.mangomaner.mangobot.manager.event.MangoEventPublisher;
import io.github.mangomaner.mangobot.model.domain.Plugins;
import io.github.mangomaner.mangobot.model.dto.config.CreateConfigRequest;
import io.github.mangomaner.mangobot.model.plugin.PluginInfo;
import io.github.mangomaner.mangobot.plugin.register.PluginRegistrar;
import io.github.mangomaner.mangobot.plugin.unregister.PluginUnloader;
import io.github.mangomaner.mangobot.service.MangobotConfigService;
import io.github.mangomaner.mangobot.service.PluginsService;
import io.github.mangomaner.mangobot.utils.FileUtils;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

@Component
@Slf4j
public class PluginManager {

    @Resource
    private MangoEventPublisher eventPublisher;

    @Resource
    private ConfigurableApplicationContext applicationContext;

    @Resource
    private PluginRegistrar pluginRegistrar;

    @Resource
    private PluginUnloader pluginUnloader;

    @Resource
    private PluginsService pluginsService;

    @Resource
    private MangobotConfigService mangobotConfigService;

    // 统一管理插件资源
    private final Map<String, PluginRuntimeWrapper> pluginRegistry = new ConcurrentHashMap<>();
    private String pluginDir = "plugins";

    public void init() {
        Path baseDir = FileUtils.getBaseDirectory();
        Path currentPlugins = baseDir.resolve("plugins");
        Path parentPlugins = baseDir.resolve("../plugins");
        
        File current = currentPlugins.toFile();
        File parent = parentPlugins.toFile();
        
        if (!current.exists() && parent.exists() && parent.isDirectory()) {
            this.pluginDir = parent.getAbsolutePath();
            log.info("检测到上一级目录存在 plugins 文件夹，将使用: {}", parent.getAbsolutePath());
        } else {
            this.pluginDir = current.getAbsolutePath();
            log.info("使用默认插件目录: {}", current.getAbsolutePath());
        }

        pluginRegistrar.registerWebComponents();
        syncPlugins();
        mangobotConfigService.init();
    }

    public File getPluginDirectory() {
        return new File(pluginDir);
    }

    /**
     * 启动时同步检测
     */
    @Transactional(rollbackFor = Exception.class)
    public void syncPlugins() {
        log.info("开始同步插件...");
        File dir = getPluginDirectory();
        if (!dir.exists()) {
            FileUtils.createDirectory(dir.toPath());
        }

        File[] files = dir.listFiles((d, name) -> name.endsWith(".jar"));
        List<File> fileList = files != null ? Arrays.asList(files) : Collections.emptyList();
        Set<String> fileNames = new HashSet<>();
        for (File f : fileList) {
            fileNames.add(f.getName());
        }

        List<Plugins> dbPlugins = pluginsService.list();

        for (Plugins p : dbPlugins) {
            if (!fileNames.contains(p.getJarName())) {
                log.info("发现残留插件记录，正在清理: {}", p.getJarName());
                uninstallPluginData(p);
            }
        }

        for (File file : fileList) {
            boolean exists = dbPlugins.stream().anyMatch(p -> p.getJarName().equals(file.getName()));
            if (!exists) {
                log.info("发现新插件文件，正在注册: {}", file.getName());
                scanAndRegister(file);
            }
        }

        List<Plugins> enabledPlugins = pluginsService.list(new LambdaQueryWrapper<Plugins>().eq(Plugins::getEnabled, 1));
        for (Plugins p : enabledPlugins) {
            File file = new File(dir, p.getJarName());
            if (file.exists()) {
                loadPlugin(file);
            }
        }

        eventPublisher.printAllListeners();
        log.info("插件同步完成");
    }

    /**
     * 处理新增文件（供 Watcher 调用）
     */
    public void handleNewFile(File file) {
        // 1. 注册
        Long pluginId = scanAndRegister(file);
        if (pluginId == null) return;

        // 2. 检查是否需要加载
        Plugins p = pluginsService.getById(pluginId);
        if (p != null && p.getEnabled() == 1) {
            loadPlugin(file);
        }
    }

    /**
     * 扫描 Jar 并注册到数据库
     * @return 插件ID，如果失败返回 null
     */
    @Transactional(rollbackFor = Exception.class)
    public Long scanAndRegister(File jarFile) {
        String jarName = jarFile.getName();
        PluginClassLoader loader = null;
        try {
            loader = PluginClassLoader.create(jarFile, getClass().getClassLoader());
            try (JarFile jar = new JarFile(jarFile)) {
                Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    if (!entry.getName().endsWith(".class")) continue;

                    String className = entry.getName().replace("/", ".").replace(".class", "");
                    // 包名检查
                    String[] parts = className.split("\\.");
                    if (!(parts.length > 3 && parts[0].equals("io") && parts[1].equals("github") && parts[3].equals("mangobot"))) {
                        continue;
                    }

                    try {
                        Class<?> clazz = loader.loadClass(className);
                        if (Plugin.class.isAssignableFrom(clazz) && !clazz.isInterface() && clazz.isAnnotationPresent(PluginDescribe.class)) {
                            PluginDescribe describe = clazz.getAnnotation(PluginDescribe.class);

                            // 1. 保存/更新 Plugins 表
                            Plugins plugin = getOrCreatePlugin(jarName);
                            plugin.setPluginName(describe.name());
                            plugin.setAuthor(describe.author());
                            plugin.setVersion(describe.version());
                            plugin.setDescription(describe.description());
                            plugin.setPackageName(className);
                            plugin.setEnabledWeb(describe.enableWeb() ? 1 : 0);
                            // 默认启用状态：如果是新记录，使用注解默认值；如果是旧记录，保持原样（但这里假设是新注册）
                            if (plugin.getId() == null) {
//                                plugin.setEnabled(describe.enable() ? 1 : 0);
                                plugin.setEnabled(0);
                            }
                            pluginsService.saveOrUpdate(plugin);

                            // 2. 解析配置 @PluginConfig / @PluginConfigs
                            mangobotConfigService.deleteByPluginId(plugin.getId()); // 重置配置

                            List<PluginConfig> configs = new ArrayList<>();
                            if (clazz.isAnnotationPresent(PluginConfigs.class)) {
                                configs.addAll(Arrays.asList(clazz.getAnnotation(PluginConfigs.class).value()));
                            }
                            if (clazz.isAnnotationPresent(PluginConfig.class)) {
                                configs.add(clazz.getAnnotation(PluginConfig.class));
                            }

                            for (PluginConfig pc : configs) {
                                CreateConfigRequest req = new CreateConfigRequest();
                                req.setPluginId(plugin.getId());
                                req.setKey(pc.key());
                                req.setValue(pc.value());
                                req.setType(pc.type());
                                req.setDesc(pc.description());
                                req.setExplain(pc.explain());
                                mangobotConfigService.registeConfigWithoutPublish(req);
                            }

                            log.info("插件注册成功: {}", jarName);
                            return plugin.getId();
                        }
                    } catch (Throwable t) {
                        // 忽略非主类加载错误
                    }
                }
            }
        } catch (Exception e) {
            log.error("扫描插件失败: {}", jarName, e);
        } finally {
            if (loader != null) {
                try { loader.close(); } catch (IOException e) {}
            }
        }
        return null;
    }

    private Plugins getOrCreatePlugin(String jarName) {
        LambdaQueryWrapper<Plugins> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Plugins::getJarName, jarName);
        Plugins p = pluginsService.getOne(wrapper);
        if (p == null) {
            p = new Plugins();
            p.setJarName(jarName);
        }
        return p;
    }

    public void loadPlugin(File jarFile) {
        String pluginId = jarFile.getName();
        if (pluginRegistry.containsKey(pluginId)) {
            return;
        }

        log.info("正在加载插件: {}", pluginId);
        PluginClassLoader loader = null;
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();

        try {
            loader = PluginClassLoader.create(jarFile, getClass().getClassLoader());
            Thread.currentThread().setContextClassLoader(loader);
            PluginRuntimeWrapper wrapper = new PluginRuntimeWrapper(pluginId, loader);

            // 查找已注册的主类
            Plugins p = pluginsService.getOne(new LambdaQueryWrapper<Plugins>().eq(Plugins::getJarName, pluginId));
            if (p == null) {
                log.error("插件未注册，无法加载: {}", pluginId);
                return;
            }

            Class<?> clazz = loader.loadClass(p.getPackageName());

            // 重新获取注解信息填充 wrapper
            if (clazz.isAnnotationPresent(PluginDescribe.class)) {
                wrapper.setDescribe(clazz.getAnnotation(PluginDescribe.class));
            }

            boolean isRequestMapping = clazz.isAnnotationPresent(MangoBotRequestMapping.class);
            Object instance = null;

            // 解析 @MangoBotRequestMapping
            if (isRequestMapping) {
                pluginRegistrar.registerController(clazz, wrapper);
                String beanName = clazz.getName();
                instance = applicationContext.getBean(beanName);
            }

            instance = instance == null ? clazz.getDeclaredConstructor().newInstance() : instance;
            Plugin plugin = (Plugin) instance;
            wrapper.setPluginInstance(plugin);

            // 注入和注册
            pluginRegistrar.injectFields(clazz, instance);
            pluginRegistrar.registerEventListeners(clazz, instance, wrapper);

            plugin.onEnable();
            pluginRegistry.put(pluginId, wrapper);
            log.info("插件加载成功: {}", pluginId);

        } catch (Exception e) {
            log.error("加载插件失败: {}", pluginId, e);
            if (loader != null) {
                try { loader.close(); } catch (IOException ex) {}
            }
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    public void unloadPlugin(String pluginId) {
        PluginRuntimeWrapper wrapper = pluginRegistry.remove(pluginId);
        if (wrapper != null) {
            log.info("正在卸载插件: {}", pluginId);
            pluginUnloader.unload(wrapper);
        }
    }

    /**
     * 彻底卸载插件（删除文件、DB、Web资源）
     */
    @Transactional(rollbackFor = Exception.class)
    public void uninstallPlugin(String pluginId) {
        // 1. 卸载运行实例
        unloadPlugin(pluginId);

        // 2. 删除文件
        File file = new File(getPluginDirectory(), pluginId);
        if (file.exists()) {
            file.delete();
        }

        // 3. 删除 DB 数据
        LambdaQueryWrapper<Plugins> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Plugins::getJarName, pluginId);
        Plugins p = pluginsService.getOne(wrapper);
        if (p != null) {
            uninstallPluginData(p);
        }
    }

    private void uninstallPluginData(Plugins p) {
        // 删除 Web 资源
        if (p.getEnabledWeb() != null && p.getEnabledWeb() == 1) {
            deleteWebResources(p.getJarName());
        }
        // 删除配置
        mangobotConfigService.deleteByPluginId(p.getId());
        // 删除插件记录
        pluginsService.removeById(p.getId());
    }

    private void deleteWebResources(String jarName) {
        String folderName = jarName.replace(".jar", "");
        Path baseDir = FileUtils.getBaseDirectory();
        Path webDir = baseDir.resolve("web").resolve(folderName);
        Path parentWebDir = baseDir.resolve("../web").resolve(folderName);
        
        File webDirFile = webDir.toFile();
        File parentWebDirFile = parentWebDir.toFile();
        
        if (webDirFile.exists()) {
            deleteDirectory(webDirFile);
        } else if (parentWebDirFile.exists()) {
            deleteDirectory(parentWebDirFile);
        }
    }

    private void deleteDirectory(File file) {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File f : files) deleteDirectory(f);
            }
        }
        file.delete();
    }

    public List<PluginInfo> getAllPluginsInfo() {
        List<Plugins> allPlugins = pluginsService.list();
        List<PluginInfo> result = new ArrayList<>();

        for (Plugins p : allPlugins) {
            boolean loaded = pluginRegistry.containsKey(p.getJarName());
            String jarNameWithoutExt = p.getJarName().replace(".jar", "");

            result.add(PluginInfo.builder()
                    .id(p.getJarName())
                    .loaded(loaded)
                    .name(p.getPluginName())
                    .author(p.getAuthor())
                    .version(p.getVersion())
                    .description(p.getDescription())
                    .enabled(p.getEnabled() == 1)
                    .enableWeb(p.getEnabledWeb() == 1)
                    .webPath("static/" + jarNameWithoutExt)
                    .build());
        }
        return result;
    }
}