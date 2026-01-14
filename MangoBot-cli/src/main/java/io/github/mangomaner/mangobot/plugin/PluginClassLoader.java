package io.github.mangomaner.mangobot.plugin;

import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

@Slf4j
public class PluginClassLoader extends URLClassLoader {

    private final JarFile jarFile;

    public PluginClassLoader(JarFile jarFile, File jarFileSource, ClassLoader parent) throws Exception {
        super(new URL[]{jarFileSource.toURI().toURL()}, parent);
        this.jarFile = jarFile;
    }

    public static PluginClassLoader create(File jarFile, ClassLoader parent) throws Exception {
        return new PluginClassLoader(new JarFile(jarFile), jarFile, parent);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            // 1. 检查是否已加载
            Class<?> c = findLoadedClass(name);
            if (c != null) return c;

            // 2. 如果本 JAR 有这个类，优先自己加载
            if (shouldLoadBySelf(name)) {
                try {
                    c = findClass(name);
                    if (resolve) resolveClass(c);
                    return c;
                } catch (ClassNotFoundException e) {
                    // 记录日志，但继续
                    log.debug("Failed to load {} from plugin JAR, falling back", name);
                }
            }

            // 3. 委托父加载器
            try {
                c = getParent().loadClass(name);
                if (resolve) resolveClass(c);
                return c;
            } catch (ClassNotFoundException e) {
                // 继续
            }

            // 4. 找不到
            throw new ClassNotFoundException(name);
        }
    }

    /**
     * 只要这个 .class 文件在本 JAR 中，就自己加载（避免加载JDK自带的核心类）
     */
    private boolean shouldLoadBySelf(String name) {
        // 跳过特殊路径
        if (name.startsWith("META-INF.") || name.contains(".versions.")) {
            return false;
        }

        // 检查本 JAR 中是否存在这个类
        String path = name.replace('.', '/').concat(".class");
        return jarFile.getJarEntry(path) != null;
    }

//    @Override
//    protected Class<?> findClass(String name) throws ClassNotFoundException {
//        // 跳过加载特殊资源路径中的类
//        // 对应报错： Exception in thread "main" java.lang.NoClassDefFoundError: META-INF/versions/9/org/sqlite/nativeimage/SqliteJdbcFeat
//        if (name.startsWith("META-INF.") || name.contains(".versions.")) {
//            throw new ClassNotFoundException("Skipped loading class from special path: " + name);
//        }
//
//        String path = name.replace('.', '/').concat(".class");
//        JarEntry entry = jarFile.getJarEntry(path);
//        if (entry == null) {
//            throw new ClassNotFoundException(name);
//        }
//
//        try (InputStream is = jarFile.getInputStream(entry)) {
//            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
//            byte[] buffer = new byte[4096];
//            int bytesRead;
//            while ((bytesRead = is.read(buffer)) != -1) {
//                byteStream.write(buffer, 0, bytesRead);
//            }
//            byte[] classBytes = byteStream.toByteArray();
//            return defineClass(name, classBytes, 0, classBytes.length);
//        } catch (Exception e) {
//            throw new ClassNotFoundException("Failed to load class: " + name, e);
//        }
//    }
}