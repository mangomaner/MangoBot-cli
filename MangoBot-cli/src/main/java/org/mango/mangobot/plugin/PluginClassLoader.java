package org.mango.mangobot.plugin;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

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
            // Step 1: 检查是否已经加载过了
            Class<?> c = findLoadedClass(name);

            if (c == null) {
                // Step 2: 先让父类加载器尝试加载（标准双亲委派）
                try {
                    c = getParent().loadClass(name);
                } catch (ClassNotFoundException ignored) {
                    // 父类加载器找不到该类
                }
            }

            if (c == null) {
                // Step 3: 只有当父类加载器没有加载该类时，才自己加载
                if (name.startsWith("java.") ||
                        name.startsWith("javax.") ||
                        name.startsWith("com.google.gson.internal.bind.ReflectiveTypeAdapterFactory$2") ||
                        name.startsWith("org.springframework.")) {
                    c = getParent().loadClass(name);
                } else {
                    c = findClass(name);
                }
            }

            if (resolve) {
                resolveClass(c);
            }

            return c;
        }
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        String path = name.replace('.', '/').concat(".class");
        JarEntry entry = jarFile.getJarEntry(path);
        if (entry == null) {
            throw new ClassNotFoundException(name);
        }

        try (InputStream is = jarFile.getInputStream(entry)) {
            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                byteStream.write(buffer, 0, bytesRead);
            }
            byte[] classBytes = byteStream.toByteArray();
            return defineClass(name, classBytes, 0, classBytes.length);
        } catch (Exception e) {
            throw new ClassNotFoundException("Failed to load class: " + name, e);
        }
    }
}