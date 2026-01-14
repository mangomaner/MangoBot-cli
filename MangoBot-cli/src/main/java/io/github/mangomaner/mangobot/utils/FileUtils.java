package io.github.mangomaner.mangobot.utils;

import org.springframework.boot.system.ApplicationHome;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class FileUtils {

    /**
     * 获取基准目录：
     * - JAR 运行：JAR 所在目录；
     * - IDE 运行：user.dir（项目根目录）。
     */
    public static Path getBaseDirectory() {
        ApplicationHome home = new ApplicationHome(FileUtils.class);
        File source = home.getSource();

        if (source.isFile() && source.getName().toLowerCase().endsWith(".jar")) {
            return source.toPath().getParent(); // JAR 同级目录
        } else {
            // 开发模式：user.dir 的父目录
            return Paths.get(System.getProperty("user.dir")).getParent();
        }
    }

    public static File ensureDirectoryExists(String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) {
            throw new IllegalArgumentException("relativePath must not be null or empty");
        }
        Path dir = getBaseDirectory().resolve(relativePath);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to ensure directory exists: " + dir.toAbsolutePath(), e);
        }
        return dir.toFile();
    }

    public static File ensureFileExists(String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) {
            throw new IllegalArgumentException("relativePath must not be null or empty");
        }
        Path filePath = getBaseDirectory().resolve(relativePath);
        try {
            Path parent = filePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            if (!Files.exists(filePath)) {
                Files.createFile(filePath);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to ensure file exists: " + filePath.toAbsolutePath(), e);
        }
        return filePath.toFile();
    }

    public static File ensureResourceAsFile(String resourcePath, String relativePath) {
        if (resourcePath == null || resourcePath.isEmpty()) {
            throw new IllegalArgumentException("resourcePath must not be null or empty");
        }
        if (relativePath == null || relativePath.isEmpty()) {
            throw new IllegalArgumentException("relativePath must not be null or empty");
        }

        Path target = getBaseDirectory().resolve(relativePath);
        if (Files.exists(target)) {
            return target.toFile();
        }

        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = FileUtils.class.getClassLoader();
        }

        try (InputStream in = loader.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new RuntimeException("Classpath resource not found: " + resourcePath);
            }
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            return target.toFile();
        } catch (IOException e) {
            throw new RuntimeException(
                    "Failed to copy resource '" + resourcePath + "' to: " + target.toAbsolutePath(), e
            );
        }
    }
}