package io.github.mangomaner.mangobot.module.file.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import io.github.mangomaner.mangobot.module.file.model.domain.BotFiles;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 发表情那个池子的取数条件。
 *
 * <p>这是"只从收藏里挑"这条约束的**唯一落点**——工具层、控制台、技能提示词都只是围绕它工作。
 * 而它丢条件时不会报错，只会静默出脏图（退回从 1800 多张没筛过的 meme 里挑），
 * 运行期根本看不出来。所以把 SQL 片段钉住。
 *
 * <p>不需要数据库：{@code getSqlSegment()} 只是把 wrapper 里的条件拼成 WHERE 片段。
 */
class FavoriteMemesQueryTest {

    /**
     * {@code LambdaQueryWrapper} 要把方法引用解析成列名，得先有实体的 lambda 缓存；
     * 平时这份缓存由 Spring 初始化 mapper 时建立，这里没有 Spring，手工建一次。
     * 不这么做的话 {@code getSqlSegment()} 会抛 "can not find lambda cache"。
     */
    @BeforeAll
    static void initLambdaCache() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), BotFiles.class);
    }

    private String sql() {
        LambdaQueryWrapper<BotFiles> wrapper = BotFilesServiceImpl.favoriteMemesWrapper();
        return wrapper.getSqlSegment();
    }

    @Test
    @DisplayName("只取 meme")
    void filtersByMemeType() {
        assertTrue(sql().contains("file_type"), "缺少 file_type 条件：" + sql());
    }

    @Test
    @DisplayName("只取已收藏的——丢掉这条就退回从全部拿")
    void filtersByFavorite() {
        assertTrue(sql().contains("favorite"), "缺少 favorite 条件：" + sql());
    }

    @Test
    @DisplayName("本地文件不能为空（没落地就发不出去）")
    void filtersOutBlankPath() {
        String sql = sql();
        assertTrue(sql().contains("file_path"), "缺少 file_path 条件：" + sql);
        assertTrue(sql().split("file_path", -1).length - 1 >= 2, "file_path 应有两道条件：" + sql);
    }

    /**
     * 有意不要求描述。描述是"模型看不了图"时代的替代品，现在模型直接看图，
     * 要求描述只会把人工收藏的图挡在外面（实测收藏的 13 张全都没描述，池子直接空了）。
     */
    @Test
    @DisplayName("不要求描述——要求了会把没描述的收藏全挡在外面")
    void doesNotRequireDescription() {
        assertFalse(sql().contains("description"), "不该有 description 条件：" + sql());
    }
}
