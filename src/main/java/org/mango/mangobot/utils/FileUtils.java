package org.mango.mangobot.utils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class FileUtils {

    public static File ensureDirectoryExists(String path) {
        File dir = new File(path);
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                throw new RuntimeException("Failed to create directory: " + path);
            }
        }
        return dir;
    }

    public static void writeToFile(String content, String filePath){
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath, StandardCharsets.UTF_8))) {
            writer.write(content);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // 获取jar包路径（防止打包后无法找到对应文件）
    public String getJarPath() {
        java.net.URL url = this.getClass().getProtectionDomain().getCodeSource().getLocation();
        String jarPath = null;
        try {
            jarPath = java.net.URLDecoder.decode(url.getFile(), "UTF-8");
            if (jarPath.endsWith(".jar")) {
                jarPath = new java.io.File(jarPath).getParentFile().getAbsolutePath();
            } else {
                jarPath = new java.io.File("").getAbsolutePath();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return jarPath;
    }
}
