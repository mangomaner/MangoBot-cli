package io.github.mangomaner.mangobot.module.agent.sim.harness;

import io.github.mangomaner.mangobot.api.MangoFileApi;
import io.github.mangomaner.mangobot.module.file.model.domain.BotFiles;
import io.github.mangomaner.mangobot.utils.FileUtils;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * "出站图字节 → fileId"的反查表，只为让模拟台能认出 bot 发的是哪张图。
 *
 * <p><b>为什么需要它</b>：{@code send_image} / {@code send_meme} 出站时帧里的图片段是
 * {@code base64://<字节>}（见 {@code MessageSendSupport.imageSegment}），<b>路上不带 fileId</b>。
 * 要判断"它复读的是不是刚才那张图"，必须从字节倒推回 id。
 *
 * <p><b>候选集很小</b>：只有本轮信封里出现过的那几张图——bot 能复读的只可能是它见过的。
 * 所以建表时只读这几个文件，不是扫全库。
 *
 * <p><b>为什么先按字节数分桶再比摘要</b>：字节数不同就一定不是同一张，先分桶能让每张出站图只做
 * 一两次摘要比对，而不是候选数那么多次。真出图时反正要读一次字节，这个代价可以忽略。
 */
@Slf4j
public final class SimImageIndex {

    /** {@code 字节数:sha256} → fileId。 */
    private final Map<String, String> byContent = new HashMap<>();

    private SimImageIndex() {
    }

    /** 为这些 fileId 建表；库里查不到、本地读不到的跳过（只告警，不打断模拟）。 */
    public static SimImageIndex of(Collection<String> fileIds) {
        SimImageIndex index = new SimImageIndex();
        for (String fileId : Set.copyOf(fileIds)) {
            BotFiles file = MangoFileApi.getFileByFileId(fileId);
            if (file == null) {
                log.warn("剧本引用了库里没有的图片 id，模拟时认不出这张图：fileId={}", fileId);
                continue;
            }
            String filePath = file.getFilePath();
            if (filePath == null || filePath.isBlank()) {
                log.warn("图片 id 没有本地路径，模拟时认不出这张图：fileId={}", fileId);
                continue;
            }
            try {
                Path resolved = FileUtils.resolvePath(filePath);
                byte[] bytes = Files.readAllBytes(resolved);
                index.byContent.put(keyOf(bytes), fileId);
            } catch (Exception e) {
                log.warn("读图片失败，模拟时认不出这张图：fileId={} path={}", fileId, filePath, e);
            }
        }
        return index;
    }

    /** 反查这张图的 fileId；不在候选集里（含空字节）返回 null。 */
    public String fileIdOf(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        return byContent.get(keyOf(bytes));
    }

    private static String keyOf(byte[] bytes) {
        return bytes.length + ":" + sha256(bytes);
    }

    private static String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            // SHA-256 一定有；这里只是不让"没有摘要算法"这种不可能的事把模拟打挂
            return "";
        }
    }
}
