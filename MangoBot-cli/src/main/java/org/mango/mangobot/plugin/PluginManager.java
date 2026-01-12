package org.mango.mangobot.plugin;

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
        // 在 PluginManager.loadPlugins() 末尾
        applicationContext.publishEvent(new PluginLoadEvent(this));
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
                        Class<?> clazz = loader.loadClass(className);
                        // System.out.println("classLoader: " + clazz.getClassLoader() + " ; " + "class: " + clazz);
                        if (Plugin.class.isAssignableFrom(clazz)) {
                            Plugin plugin = (Plugin) clazz.getDeclaredConstructor().newInstance();
                            PluginContext context = new PluginContext(applicationContext);
                            plugin.onEnable(context);
                            plugins.add(plugin);
                            classLoaders.put(clazz.getName(), loader);

                            registerHandlers(plugin);

                            log.info("已加载插件: " + clazz.getName());
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

    private void registerHandlers(Object pluginInstance) {

    }

    private boolean isMessageHandlerMethod(Method method) {
        return false;
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
    }
}