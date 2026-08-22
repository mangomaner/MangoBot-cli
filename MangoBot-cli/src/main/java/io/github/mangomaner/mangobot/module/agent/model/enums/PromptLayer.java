package io.github.mangomaner.mangobot.module.agent.model.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 三层提示词分层：各 layer 独立走继承链解析。
 *
 * <p>framework 为系统级硬规则（工具调用协议、信封格式），改错可能影响 bot 正常回复；
 * persona / behavior 为用户规范，普通二次确认即可。
 */
@Getter
@RequiredArgsConstructor
public enum PromptLayer {
    FRAMEWORK("framework", "框架规范", true),
    PERSONA("persona", "人格规范", false),
    BEHAVIOR("behavior", "行为规范", false);

    @EnumValue
    private final String code;
    private final String description;
    /** 是否高危层（保存前需校验关键段落、编辑需严重性警告） */
    private final boolean highRisk;

    public static PromptLayer fromCode(String code) {
        for (PromptLayer layer : values()) {
            if (layer.getCode().equalsIgnoreCase(code)) {
                return layer;
            }
        }
        throw new IllegalArgumentException("Unknown prompt layer: " + code);
    }
}
