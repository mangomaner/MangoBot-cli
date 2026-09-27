package io.github.mangomaner.mangobot.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link FileUtils#mediaTypeOf} 的映射。
 *
 * <p>送图给模型的两条路（每轮附带送、工具结果里带）都靠它判断后缀能不能送，
 * 映射错了的表现是"图被静默跳过"——不报错，只是模型看不到。
 */
class FileUtilsMediaTypeTest {

    @Test
    @DisplayName("认得出的图片后缀")
    void mapsKnownImageExtensions() {
        assertEquals("image/jpeg", FileUtils.mediaTypeOf("data/meme/a.jpg"));
        assertEquals("image/jpeg", FileUtils.mediaTypeOf("data/meme/a.JPEG"));
        assertEquals("image/png", FileUtils.mediaTypeOf("data/meme/a.png"));
        assertEquals("image/gif", FileUtils.mediaTypeOf("data/meme/a.gif"));
        assertEquals("image/webp", FileUtils.mediaTypeOf("data/meme/a.webp"));
    }

    @Test
    @DisplayName("认不出的返回 null，不当成图片送出去")
    void rejectsUnknownThings() {
        assertNull(FileUtils.mediaTypeOf("data/meme/a.txt"));
        assertNull(FileUtils.mediaTypeOf("data/meme/noextension"));
        assertNull(FileUtils.mediaTypeOf(null));
    }

    @Test
    @DisplayName("点只取最后一段：带点的目录名不会误判")
    void usesLastDot() {
        assertEquals("image/png", FileUtils.mediaTypeOf("data/some.dir.v2/a.png"));
    }
}
