package io.github.mangomaner.mangobot.module.agent.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Bot 类型：决定该 bot 使用哪套「人格规范 / 行为规范」默认模板，以及是否注入风格示范。
 *
 * <p>类型值以纯字符串存于 bot_configs（config_key=bot.type），三类只影响「bot 个性」，不影响框架规范。
 */
@Getter
@RequiredArgsConstructor
public enum BotType {
    HUMAN("human", "模仿人类"),
    ROLEPLAY("roleplay", "角色扮演"),
    INSTRUCTION("instruction", "指令遵循");

    private final String code;
    private final String description;

    public static BotType fromCode(String code) {
        for (BotType type : values()) {
            if (type.getCode().equalsIgnoreCase(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown bot type: " + code);
    }

    /** 兜底默认：模仿人类（现有行为，不配置任何类型时保持原样） */
    public static BotType defaultType() {
        return HUMAN;
    }
}
