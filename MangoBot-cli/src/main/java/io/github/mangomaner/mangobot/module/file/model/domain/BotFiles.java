package io.github.mangomaner.mangobot.module.file.model.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import lombok.Data;

/**
 * 
 * @TableName files
 */
@TableName(value ="bot_files")
@Data
public class BotFiles implements Serializable {
    /**
     * 
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 
     */
    private String fileType;

    /**
     * 
     */
    private String fileId;

    /**
     * 
     */
    private String url;

    /**
     * 
     */
    private String filePath;

    /**
     * 
     */
    private Integer subType;

    /**
     * 
     */
    private Integer fileSize;

    /**
     *
     */
    private String description;

    /**
     * 是否被收藏：1 收藏、0 或 null 未收藏。
     *
     * <p>只对表情（{@code file_type='meme'}）有意义。发表情的能力**只从收藏里挑**，
     * 所以这是人工给表情池设的质量闸门——库里 1800 多个 meme 是群里捡来的，没有筛选。
     *
     * <p>列是老库后加的（本项目没有迁移机制，见 {@code FileSchema}），所以历史行是 null 而不是 0，
     * 读的时候按「非 1 即未收藏」处理。
     */
    private Integer favorite;

    /**
     *
     */
    private Long createTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}