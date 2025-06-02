package org.mango.mangobot.plugin;

import jakarta.annotation.Resource;
import org.mango.mangobot.annotation.QQ.method.AtMessage;
import org.mango.mangobot.annotation.QQ.method.AtTextImageReplyMessage;
import org.mango.mangobot.annotation.QQ.method.PokeMessage;
import org.mango.mangobot.annotation.QQ.method.TextMessage;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

@Component
public class PluginManager {
    @Resource
    private ApplicationContext applicationContext;
    @Resource
    private MessageDispatcher messageDispatcher;
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

                    Class<?> clazz = loader.loadClass(className);

                    if (Plugin.class.isAssignableFrom(clazz)) {
                        Plugin plugin = (Plugin) clazz.getDeclaredConstructor().newInstance();
                        PluginContext context = new PluginContext(applicationContext);
                        plugin.onEnable(context);
                        plugins.add(plugin);
                        classLoaders.put(clazz.getName(), loader);

                        // 注册消息处理方法
                        registerHandlers(plugin);

                        System.out.println("已加载插件: " + clazz.getName());
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void registerHandlers(Object pluginInstance) {
        for (Method method : pluginInstance.getClass().getDeclaredMethods()) {
            if (isMessageHandlerMethod(method)) {
                messageDispatcher.registerHandlerMethod(pluginInstance, method);
            }
        }
    }

    private boolean isMessageHandlerMethod(Method method) {
        return method.isAnnotationPresent(TextMessage.class)
                || method.isAnnotationPresent(AtMessage.class)
                || method.isAnnotationPresent(PokeMessage.class)
                || method.isAnnotationPresent(AtTextImageReplyMessage.class);
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