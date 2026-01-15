package io.github.mangomaner.mangobot.utils;

import org.springframework.boot.system.ApplicationHome;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.Map;

public class FileUtils {

    private static final Object WRITE_LOCK = new Object();

    /**
     * 校验路径安全性，防止路径穿越
     */
    private static void validatePath(String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) {
            throw new IllegalArgumentException("Path must not be null or empty");
        }
        // 防止路径穿越 (简单的字符串检查)
        if (relativePath.contains("..")) {
            throw new SecurityException("Invalid path: " + relativePath + " contains illegal characters");
        }
    }

    /**
     * 读取base路径下某个文件的内容（若不存在则先创建再读取）
     */
    public static String readFileContent(String relativePath) {
        validatePath(relativePath);
        File file = ensureFileExists(relativePath);
        try {
            return Files.readString(file.toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file: " + file.getAbsolutePath(), e);
        }
    }

    /**
     * 写入内容到base路径下的某个文件（加sync锁）
     */
    public static void writeFileContent(String relativePath, String content) {
        validatePath(relativePath);
        synchronized (WRITE_LOCK) {
            File file = ensureFileExists(relativePath);
            try {
                Files.writeString(file.toPath(), content, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            } catch (IOException e) {
                throw new RuntimeException("Failed to write to file: " + file.getAbsolutePath(), e);
            }
        }
    }

    /**
     * 读取base路径下application.yml文件的内容，返回key-value格式的map
     */
    public static Map<String, Object> readApplicationYml() {
        String relativePath = "application.yml";
        // 这里不强制创建，如果不存在则返回空Map
        Path ymlPath = getBaseDirectory().resolve(relativePath);
        if (!Files.exists(ymlPath)) {
            return Collections.emptyMap();
        }

        try (InputStream in = Files.newInputStream(ymlPath)) {
            Yaml yaml = new Yaml();
            return yaml.load(in);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read application.yml", e);
        }
    }

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

    /**
     * 确保目录存在，不存在则创建
     */
    public static File ensureDirectoryExists(String relativePath) {
        validatePath(relativePath);
        Path dir = getBaseDirectory().resolve(relativePath);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to ensure directory exists: " + dir.toAbsolutePath(), e);
        }
        return dir.toFile();
    }

    /**
     * 确保文件存在，不存在则创建
     */
    public static File ensureFileExists(String relativePath) {
        validatePath(relativePath);
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

    /**
     * 将类路径下的资源复制到指定路径下
     * @param resourcePath 类路径下的资源路径
     * @param relativePath 相对路径
     * @return 目标文件
     */
    public static File copyResourceAsFile(String resourcePath, String relativePath) {
        if (resourcePath == null || resourcePath.isEmpty()) {
            throw new IllegalArgumentException("resourcePath must not be null or empty");
        }
        validatePath(relativePath);

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