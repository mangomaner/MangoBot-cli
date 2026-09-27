package io.github.mangomaner.mangobot.module.agent.model.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Skill 目录文件树节点。
 *
 * <p>目录的 children 已按「目录在前、名字排序」排好，前端 n-tree 可直接用。
 * <p>content 只在客户端选中单个文件、再次拖拽/点选时按需读取，不随整棵树下发（避免把
 * references/ 里的大文件一起读完）。
 */
@Data
public class SkillFileNodeVO {
    /** 仅展示用的节点名（文件/目录的 basename） */
    private String name;
    /** 相对 skill 目录的路径，如 "SKILL.md"、""references/doc.md（目录为前缀） */
    private String path;
    /** 是否为目录 */
    private Boolean directory;
    /** 文件内容（仅叶子节点、且命中某次读取时才有；树里不携带） */
    private String content;
    /** 子节点 */
    private List<SkillFileNodeVO> children = new ArrayList<>();
}