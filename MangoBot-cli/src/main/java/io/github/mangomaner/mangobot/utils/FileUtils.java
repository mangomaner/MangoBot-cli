package io.github.mangomaner.mangobot.utils;

import org.springframework.boot.system.ApplicationHome;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Collections;
import java.util.Map;

/**
 * 通用文件操作工具类
 * <p>
 * 提供基于应用根目录的文件读写、创建、资源复制等功能，
 * 并包含路径安全校验机制。
 */
public class FileUtils {

    private static final Object WRITE_LOCK = new Object();

    // ========================================================================
    // 基准目录
    // ========================================================================

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
            // 开发模式：user.dir 的目录
            return Paths.get(System.getProperty("user.dir"));
        }
    }

    /**
     * 解析相对于基准目录的路径，并进行安全校验（防止路径穿越）
     *
     * @param relativePath 相对路径
     * @return 解析后的绝对路径
     */
    public static Path resolvePath(String relativePath) {
        return resolvePath(getBaseDirectory(), relativePath);
    }

    /**
     * 解析相对于指定根目录的路径，并进行安全校验
     *
     * @param root         根目录
     * @param relativePath 相对路径
     * @return 解析后的绝对路径
     */
    public static Path resolvePath(Path root, String relativePath) {
        if (root == null) {
            throw new IllegalArgumentException("Root path must not be null");
        }
        if (relativePath == null || relativePath.isEmpty()) {
            return root;
        }
        Path resolved = root.resolve(relativePath).normalize();
        if (!resolved.startsWith(root)) {
            throw new SecurityException("Invalid path: " + relativePath + " traverses out of root: " + root);
        }
        return resolved;
    }

    // ========================================================================
    // 读取文件
    // ========================================================================

    /**
     * 读取文件内容为字符串
     */
    public static String readString(Path path) {
        if (!Files.exists(path)) {
            throw new RuntimeException("File not found: " + path.toAbsolutePath());
        }
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file: " + path.toAbsolutePath(), e);
        }
    }

    public static String readString(String relativePath) {
        return readString(resolvePath(relativePath));
    }

    /**
     * 读取YAML文件
     */
    public static Map<String, Object> readYaml(String relativePath) {
        Path path = resolvePath(relativePath);
        if (!Files.exists(path)) {
            return Collections.emptyMap();
        }
        try (InputStream in = Files.newInputStream(path)) {
            Yaml yaml = new Yaml();
            return yaml.load(in);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read YAML file: " + path.toAbsolutePath(), e);
        }
    }

    // ========================================================================
    // 写文件
    // ========================================================================

    /**
     * 写入字符串到文件（覆盖模式，线程安全）
     */
    public static void writeString(Path path, String content) {
        synchronized (WRITE_LOCK) {
            createFile(path); // Ensure file and parent dirs exist
            try {
                Files.writeString(path, content, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            } catch (IOException e) {
                throw new RuntimeException("Failed to write to file: " + path.toAbsolutePath(), e);
            }
        }
    }

    public static void writeString(String relativePath, String content) {
        writeString(resolvePath(relativePath), content);
    }

    // ========================================================================
    // 创建文件
    // ========================================================================

    /**
     * 确保文件存在（如果不存在则创建，包括父目录）
     */
    public static File createFile(Path path) {
        try {
            if (Files.exists(path)) {
                return path.toFile();
            }
            createParentDirectories(path);
            Files.createFile(path);
            return path.toFile();
        } catch (IOException e) {
            throw new RuntimeException("Failed to create file: " + path.toAbsolutePath(), e);
        }
    }

    public static File createFile(String relativePath) {
        return createFile(resolvePath(relativePath));
    }

    /**
     * 确保目录存在
     */
    public static File createDirectory(Path path) {
        try {
            if (!Files.exists(path)) {
                Files.createDirectories(path);
            }
            return path.toFile();
        } catch (IOException e) {
            throw new RuntimeException("Failed to create directory: " + path.toAbsolutePath(), e);
        }
    }

    public static File createDirectory(String relativePath) {
        return createDirectory(resolvePath(relativePath));
    }

    /**
     * 创建父目录
     */
    public static void createParentDirectories(Path path) {
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to create parent directories for: " + path.toAbsolutePath(), e);
        }
    }

    // ========================================================================
    // 将jar包内resources的资源文件复制到目标文件
    // ========================================================================

    /**
     * 将Classpath下的资源复制到文件系统
     */
    public static File copyResource(String resourcePath, Path targetPath) {
        if (Files.exists(targetPath)) {
            return targetPath.toFile();
        }

        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = FileUtils.class.getClassLoader();
        }

        try (InputStream in = loader.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new RuntimeException("Classpath resource not found: " + resourcePath);
            }
            createParentDirectories(targetPath);
            Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
            return targetPath.toFile();
        } catch (IOException e) {
            throw new RuntimeException("Failed to copy resource '" + resourcePath + "' to: " + targetPath.toAbsolutePath(), e);
        }
    }

    public static File copyResource(String resourcePath, String relativePath) {
        return copyResource(resourcePath, resolvePath(relativePath));
    }
}
