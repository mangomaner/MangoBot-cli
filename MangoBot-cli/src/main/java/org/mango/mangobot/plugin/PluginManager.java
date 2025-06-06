package org.mango.mangobot.plugin;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.mango.mangobot.annotation.PluginPriority;
import org.mango.mangobot.annotation.QQ.method.*;
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
    @Resource
    private Map<Method, RegisteredHandler> messageHandler;
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

                    // 跳过 module-info.class 和其他非法类名
                    if (className.contains("module-info")) {
                        System.out.println("跳过模块信息类: " + className);
                        continue;
                    }
                    try{
                        Class<?> clazz = loader.loadClass(className);
                        if (Plugin.class.isAssignableFrom(clazz)) {
                            Plugin plugin = (Plugin) clazz.getDeclaredConstructor().newInstance();
                            PluginContext context = new PluginContext(applicationContext);
                            plugin.onEnable(context);
                            plugins.add(plugin);
                            classLoaders.put(clazz.getName(), loader);

                            registerHandlers(plugin);

                            System.out.println("已加载插件: " + clazz.getName());
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
        for (Method method : pluginInstance.getClass().getDeclaredMethods()) {
            if (isMessageHandlerMethod(method)) {
                int priority = 100;
                if(method.isAnnotationPresent(PluginPriority.class)){
                    priority = method.getAnnotation(PluginPriority.class).value();
                }
                messageHandler.putIfAbsent(method, new RegisteredHandler(method, pluginInstance, List.of(method.getAnnotations()), priority));
            }
        }
    }

    private boolean isMessageHandlerMethod(Method method) {
        return method.isAnnotationPresent(TextMessage.class)
                || method.isAnnotationPresent(AtMessage.class)
                || method.isAnnotationPresent(PokeMessage.class)
                || method.isAnnotationPresent(AudioMessage.class)
                || method.isAnnotationPresent(ImageMessage.class)
                || method.isAnnotationPresent(ReplyMessage.class)
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