package io.github.mangomaner.mangobot.plugin;

import io.github.mangomaner.mangobot.annotation.MangoBot;
import io.github.mangomaner.mangobot.annotation.web.MangoBotRequestMapping;
import io.github.mangomaner.mangobot.manager.event.MangoEventPublisher;
import io.github.mangomaner.mangobot.plugin.register.PluginRegistrar;
import io.github.mangomaner.mangobot.plugin.unregister.PluginUnloader;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Map;
import java.util.Objects;
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

    // 统一管理插件资源
    private final Map<String, PluginRuntimeWrapper> pluginRegistry = new ConcurrentHashMap<>();
    private String pluginDir = "plugins";

    @PostConstruct
    public void init() {
        // 自动探测插件目录
        File current = new File("plugins");
        File parent = new File("../plugins");
        if (!current.exists() && parent.exists() && parent.isDirectory()) {
            this.pluginDir = "../plugins";
            log.info("检测到上一级目录存在 plugins 文件夹，将使用: {}", parent.getAbsolutePath());
        } else {
            log.info("使用默认插件目录: {}", current.getAbsolutePath());
        }

        // 初始化 Web 组件
        pluginRegistrar.registerWebComponents();
    }

    public File getPluginDirectory() {
        return new File(pluginDir);
    }

    public void loadPlugins() {
        File dir = getPluginDirectory();
        if (!dir.exists() || !dir.isDirectory()) {
            System.out.println("插件目录不存在: " + dir.getAbsolutePath());
            return;
        }

        for (File file : Objects.requireNonNull(dir.listFiles())) {
            if (file.getName().endsWith(".jar")) {
                loadPlugin(file);
            }
        }

        eventPublisher.printAllListeners();
    }

    /**
     * 加载指定路径的插件
     * @param path 插件文件路径
     */
    public void loadPlugin(String path) {
        File file = new File(path);
        if (!file.exists()) {
            log.error("插件文件不存在: {}", path);
            return;
        }
        loadPlugin(file);
    }

    public void loadPlugin(File jarFile) {
        String pluginId = jarFile.getName();
        if (pluginRegistry.containsKey(pluginId)) {
            log.warn("插件 {} 已加载，跳过", pluginId);
            return;
        }

        PluginClassLoader loader = null;
        try {
            loader = PluginClassLoader.create(jarFile, getClass().getClassLoader());
            PluginRuntimeWrapper wrapper = new PluginRuntimeWrapper(pluginId, loader);

            // 使用 try-with-resources 确保 JarFile 在遍历后立即关闭
            try (JarFile jar = new JarFile(jarFile)) {
                Enumeration<JarEntry> entries = jar.entries();

                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    if (entry.getName().endsWith(".class")) {
                        String className = entry.getName()
                                .replace("/", ".")
                                .replace(".class", "");

                        if (entry.getName().equals("module-info.class") ||
                                entry.getName().startsWith("META-INF/versions/") && entry.getName().endsWith("/module-info.class")) {
                            continue;
                        }
                        try {
                            Class<?> clazz = loader.loadClass(className);

                            boolean isPlugin = Plugin.class.isAssignableFrom(clazz) && !clazz.isInterface();
                            boolean hasMangoBot = clazz.isAnnotationPresent(MangoBot.class);
                            boolean isRequestMapping = clazz.isAnnotationPresent(MangoBotRequestMapping.class);

                            Object instance = null;

                            if (hasMangoBot) {
                                try {
                                    if (isRequestMapping) {
                                        pluginRegistrar.registerController(clazz, wrapper);
                                        String beanName = clazz.getName();
                                        instance = applicationContext.getBean(beanName);
                                    }

                                    if (isPlugin) {
                                        instance = instance == null ? clazz.getDeclaredConstructor().newInstance() : instance;
                                        Plugin plugin = (Plugin) instance;
                                        wrapper.setPluginInstance(plugin);

                                        PluginContext context = new PluginContext(applicationContext);

                                        pluginRegistrar.registerEventListeners(clazz, instance, wrapper);
                                        pluginRegistrar.injectApiServices(clazz, instance);

                                        plugin.onEnable(context);
                                        log.info("已加载插件主类: {}", clazz.getName());
                                    } else {
                                        instance = instance == null ? clazz.getDeclaredConstructor().newInstance() : instance;
                                        pluginRegistrar.registerEventListeners(clazz, instance, wrapper);
                                        pluginRegistrar.injectApiServices(clazz, instance);
                                    }

                                } catch (ReflectiveOperationException e) {
                                    log.warn("加载或初始化类 {} 失败", className, e);
                                }
                            }
                        } catch (Exception e) {
                            log.warn("加载类 {} 失败: {}", className, e.toString());
                        }
                    }
                }
            }

            pluginRegistry.put(pluginId, wrapper);
            log.info("插件 {} 加载完成", pluginId);
        } catch (Exception e) {
            log.error("加载插件 {} 失败", jarFile.getName(), e);
            // 如果加载失败，确保关闭 ClassLoader 以释放文件句柄
            if (loader != null) {
                try {
                    loader.close();
                } catch (IOException ex) {
                    log.warn("关闭加载失败的插件 ClassLoader 出错", ex);
                }
            }
        }
    }

    /**
     * 卸载所有插件
     */
    public void unloadPlugins() {
        if (pluginRegistry.isEmpty()) {
            return;
        }

        // 创建副本进行遍历，避免并发修改异常
        new java.util.ArrayList<>(pluginRegistry.keySet()).forEach(this::unloadPlugin);

        pluginRegistry.clear();
        log.info("所有插件已卸载");
    }

    /**
     * 卸载指定插件
     * @param pluginId 插件ID (通常是Jar文件名)
     */
    public void unloadPlugin(String pluginId) {
        PluginRuntimeWrapper wrapper = pluginRegistry.remove(pluginId);
        if (wrapper == null) {
            log.warn("尝试卸载不存在的插件: {}", pluginId);
            return;
        }
        pluginUnloader.unload(wrapper);
    }

    /**
     * 获取已加载的插件ID列表
     */
    public java.util.List<String> getLoadedPluginIds() {
        return new java.util.ArrayList<>(pluginRegistry.keySet());
    }
}
