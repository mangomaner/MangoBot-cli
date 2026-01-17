package io.github.mangomaner.mangobot.plugin;

import io.github.mangomaner.mangobot.annotation.MangoBot;
import io.github.mangomaner.mangobot.annotation.MangoBotApiService;
import io.github.mangomaner.mangobot.annotation.PluginDescribe;
import io.github.mangomaner.mangobot.annotation.web.MangoBotRequestMapping;
import io.github.mangomaner.mangobot.manager.event.MangoEventPublisher;
import io.github.mangomaner.mangobot.plugin.register.PluginRegistrar;
import io.github.mangomaner.mangobot.plugin.unregister.PluginUnloader;
import io.github.mangomaner.mangobot.model.plugin.PluginInfo;
import io.github.mangomaner.mangobot.service.OneBotApiService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
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
    private OneBotApiService oneBotApiService;

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
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();

        try {
            // 思路：
            // 1. 创建新的 PluginClassLoader 类加载器（每个插件分配一个）
            // 2. 暂存现在的类加载器（当前线程的类加载器）
            // 3. 将当前线程的类加载器设置为新建的 PluginClassLoader，用于执行 Plugin 接口的 onEnable() 方法
            // 4. 执行完后，将当前线程的类加载器设置回原来的类加载器
            //
            // 原理：每个新建的类加载器负责加载一个“子程序”，拥有独立的类空间，可以避免包名冲突和版本问题（也就是说，每个类加载器单独管理一个jar包）
            loader = PluginClassLoader.create(jarFile, getClass().getClassLoader());
            Thread.currentThread().setContextClassLoader(loader);

            PluginRuntimeWrapper wrapper = new PluginRuntimeWrapper(pluginId, loader);

            // 下面开始循环jar包中的类，找到符合条件的类进行加载
            try (JarFile jar = new JarFile(jarFile)) {
                Enumeration<JarEntry> entries = jar.entries();

                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    if (entry.getName().endsWith(".class")) {
                        String className = entry.getName()
                                .replace("/", ".")
                                .replace(".class", "");

                        // 对类名进行解析，只对符合约定好规则的类进行加载
                        String[] classNameParts = className.split("\\.");
                        if(!(classNameParts.length > 3 &&
                                classNameParts[0].equals("io") &&
                                classNameParts[1].equals("github") &&
                                classNameParts[3].equals("mangobot"))
                        ){
                            continue;
                        }

                        try {
                            Class<?> clazz = loader.loadClass(className);

                            boolean isPlugin = Plugin.class.isAssignableFrom(clazz) && !clazz.isInterface();
                            boolean isRequestMapping = clazz.isAnnotationPresent(MangoBotRequestMapping.class);

                            Object instance = null;

                            if (isPlugin) {
                                try {
                                    // 解析 @MangoBotRequestMapping
                                    if (isRequestMapping) {
                                        pluginRegistrar.registerController(clazz, wrapper);
                                        String beanName = clazz.getName();
                                        instance = applicationContext.getBean(beanName);
                                    }

                                    // 解析 PluginDescribe 注解
                                    if (clazz.isAnnotationPresent(PluginDescribe.class)) {
                                        PluginDescribe describe = clazz.getAnnotation(PluginDescribe.class);
                                        wrapper.setDescribe(describe);
                                        log.info("发现插件描述: name={}, version={}", describe.name(), describe.version());
                                    }

                                    instance = instance == null ? clazz.getDeclaredConstructor().newInstance() : instance;
                                    Plugin plugin = (Plugin) instance;
                                    wrapper.setPluginInstance(plugin);

                                    pluginRegistrar.injectApiServices(clazz, instance); // 注入 OneBotApiService
                                    pluginRegistrar.registerEventListeners(clazz, instance, wrapper); // 注册事件监听器

                                    plugin.onEnable();
                                    log.info("已加载插件主类: {}", clazz.getName());

                                } catch (ReflectiveOperationException e) {
                                    log.error("加载或初始化类 {} 失败", className, e);
                                }
                            }
                        } catch (Throwable e) {
                            log.error("加载类 {} 失败: {}", className, e.toString());
                            throw new RuntimeException("加载类" + className + "失败: ");
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
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
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
        new ArrayList<>(pluginRegistry.keySet()).forEach(this::unloadPlugin);

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
    public List<String> getLoadedPluginIds() {
        return new ArrayList<>(pluginRegistry.keySet());
    }

    /**
     * 获取已加载插件的详细信息列表
     */
    public List<PluginInfo> getLoadedPluginsInfo() {
        List<PluginInfo> list = new java.util.ArrayList<>();
        for (Map.Entry<String, PluginRuntimeWrapper> entry : pluginRegistry.entrySet()) {
            String id = entry.getKey();
            PluginRuntimeWrapper wrapper = entry.getValue();
            
            PluginDescribe describe = wrapper.getDescribe();
            PluginInfo.PluginInfoBuilder builder = PluginInfo.builder()
                    .id(id)
                    .loaded(true);

            if (describe != null) {
                builder.name(describe.name())
                       .author(describe.author())
                       .version(describe.version())
                       .description(describe.description());
            } else {
                builder.name("Unknown")
                       .author("Unknown")
                       .version("Unknown")
                       .description("");
            }
            list.add(builder.build());
        }
        return list;
    }
}
