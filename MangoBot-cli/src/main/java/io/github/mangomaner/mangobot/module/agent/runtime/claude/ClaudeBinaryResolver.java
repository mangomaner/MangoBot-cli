package io.github.mangomaner.mangobot.module.agent.runtime.claude;

import io.github.mangomaner.mangobot.utils.FileUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * 按平台解析 claude 可执行文件路径。
 *
 * <p>解析顺序：
 * <ol>
 *   <li>{@code mangobot.claude.binary-path} 显式配置</li>
 *   <li>环境变量 {@code MANGOBOT_CLAUDE_BINARY}</li>
 *   <li>{@code <base>/runtime/claude/<os>-<arch>/claude[.exe]} —— 发布包内置位置，也是新增平台的扩展口。
 *       {@code <base>} 含中文也没问题，实测非 ASCII 安装路径能正常启动</li>
 *   <li>{@code <user.home>/.local/bin/claude[.exe]} —— 官方安装器的默认位置</li>
 *   <li>PATH 查找</li>
 * </ol>
 *
 * <p>六种平台组合（win32/darwin/linux × x64/arm64）已全部映射，但当前只在 Windows 上验证。
 * 新增平台只需按 {@code runtime/claude/<os>-<arch>/} 放文件，无需改代码。
 *
 * <p>注意：本类是仓库内唯一出现 {@code .exe} 字面量的地方，刻意隔离在此单文件。
 */
@Slf4j
public final class ClaudeBinaryResolver {

    public static final String ENV_OVERRIDE = "MANGOBOT_CLAUDE_BINARY";
    private static final String DIST_ROOT = "runtime/claude";
    private static final String BINARY_BASENAME = "claude";
    private static final String WINDOWS_EXE_SUFFIX = ".exe";

    private ClaudeBinaryResolver() {
    }

    /**
     * 解析 claude 可执行文件路径，找不到时返回空。
     */
    public static Optional<Path> resolve(ClaudeRuntimeProperties properties) {
        Path fromConfig = firstExisting(properties == null ? null : properties.getBinaryPath());
        if (fromConfig != null) {
            return Optional.of(fromConfig);
        }

        Path fromEnv = firstExisting(System.getenv(ENV_OVERRIDE));
        if (fromEnv != null) {
            return Optional.of(fromEnv);
        }

        Path fromDist = firstExisting(distPath().toString());
        if (fromDist != null) {
            return Optional.of(fromDist);
        }

        Path fromUserHome = firstExisting(userHomePath().toString());
        if (fromUserHome != null) {
            return Optional.of(fromUserHome);
        }

        return fromPath();
    }

    /** 当前平台标识，如 {@code win32-x64}、{@code darwin-arm64}、{@code linux-x64}。 */
    public static String platformKey() {
        return osKey() + "-" + archKey();
    }

    /** 可执行文件名，Windows 带 .exe 后缀。 */
    public static String binaryName() {
        return isWindows() ? BINARY_BASENAME + WINDOWS_EXE_SUFFIX : BINARY_BASENAME;
    }

    /** 发布包内置路径：{@code <base>/runtime/claude/<os>-<arch>/claude[.exe]}。 */
    public static Path distPath() {
        return FileUtils.resolvePath(DIST_ROOT + "/" + platformKey() + "/" + binaryName());
    }

    /** 官方安装器默认位置：{@code <user.home>/.local/bin/claude[.exe]}。 */
    public static Path userHomePath() {
        return Paths.get(System.getProperty("user.home"), ".local", "bin", binaryName());
    }

    private static Path firstExisting(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        Path path = Paths.get(raw.trim());
        return Files.isRegularFile(path) ? path.toAbsolutePath() : null;
    }

    private static Optional<Path> fromPath() {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null || pathEnv.isBlank()) {
            return Optional.empty();
        }
        String name = binaryName();
        for (String dir : pathEnv.split(File.pathSeparator)) {
            if (dir.isBlank()) {
                continue;
            }
            try {
                Path candidate = Paths.get(dir).resolve(name);
                if (Files.isRegularFile(candidate)) {
                    return Optional.of(candidate.toAbsolutePath());
                }
            } catch (Exception e) {
                log.debug("Skip invalid PATH entry: {}", dir);
            }
        }
        return Optional.empty();
    }

    private static boolean isWindows() {
        return osKey().startsWith("win");
    }

    private static String osKey() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            return "win32";
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return "darwin";
        }
        if (os.contains("linux")) {
            return "linux";
        }
        return os.replaceAll("[^a-z0-9]", "");
    }

    private static String archKey() {
        String arch = System.getProperty("os.arch", "").toLowerCase();
        if (arch.contains("aarch64") || arch.contains("arm64")) {
            return "arm64";
        }
        if (arch.contains("amd64") || arch.contains("x86_64") || arch.contains("x64")) {
            return "x64";
        }
        return arch.replaceAll("[^a-z0-9]", "");
    }
}
