package io.github.mangomaner.mangobot.plugin;

import io.github.mangomaner.mangobot.annotation.MangoBot;
import io.github.mangomaner.mangobot.annotation.MangoBotEventListener;
import io.github.mangomaner.mangobot.manager.event.MangoEventPublisher;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

@Component
@Slf4j
public class PluginManager {
    @Resource
    private MangoEventPublisher eventPublisher;

    @Resource
    private ApplicationContext applicationContext;
    private final List<Plugin> plugins = new ArrayList<>();
    private final Map<String, PluginClassLoader> classLoaders = new HashMap<>();
    private final String PLUGIN_DIR = "plugins";

    public void loadPlugins() {
        File dir = new File(PLUGIN_DIR);
        if (!dir.exists() || !dir.isDirectory()) {
            System.out.println("插件目录不存在");
            return;
        }

        for (File file : Objects.requireNonNull(dir.listFiles())) {
            if (file.getName().endsWith(".jar")) {
                loadPlugin(file);
            }
        }
    }

    private void loadPlugin(File jarFile) {
        try {
            PluginClassLoader loader = PluginClassLoader.create(jarFile, getClass().getClassLoader());
            JarFile jar = new JarFile(jarFile);
            Enumeration<JarEntry> entries = jar.entries();

            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.getName().endsWith(".class")) {
                    String className = entry.getName()
                            .replace("/", ".")
                            .replace(".class", "");

                    if (entry.getName().equals("module-info.class") ||
                            entry.getName().startsWith("META-INF/versions/") && entry.getName().endsWith("/module-info.class")) {
                        log.info("跳过模块描述符: " + entry.getName());
                        continue;
                    }
                    try{
                        // 加载类的元信息
                        Class<?> clazz = loader.loadClass(className);
                        // 若遇到继承了插件接口/标记了@MangoBot的类，则直接实例化
                        if (Plugin.class.isAssignableFrom(clazz)) {
                            Plugin plugin = (Plugin) clazz.getDeclaredConstructor().newInstance();
                            PluginContext context = new PluginContext(applicationContext);
                            plugin.onEnable(context);
                            plugins.add(plugin);
                            classLoaders.put(clazz.getName(), loader);

                            // registerHandlers(plugin);

                            log.info("已加载插件: " + clazz.getName());
                        }

                        if (clazz.isAnnotationPresent(MangoBot.class)){
                            registerHandlers(clazz.getDeclaredConstructor().newInstance());
                        }
                    } catch (Exception e){
                        log.warn(e.toString());
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void registerHandlers(Object instance) {
        Method[] methods = instance.getClass().getDeclaredMethods();
        for (Method method : methods) {
            if (method.isAnnotationPresent(MangoBotEventListener.class)) {
                eventPublisher.registerListener(method, instance);
                // log.debug("注册插件 listener: {}", method.getName());
            }
        }
    }

    public void unloadPlugins() {
        plugins.forEach(Plugin::onDisable);
        plugins.clear();
        classLoaders.forEach((name, loader) -> {
            try {
                loader.close();
            } catch (IOException ignored) {}
        });
        classLoaders.clear();
        // Ideally we should also unregister listeners from MangoEventPublisher
        // But currently MangoEventPublisher doesn't support unregistering.
        // This might be a future improvement.
    }
}