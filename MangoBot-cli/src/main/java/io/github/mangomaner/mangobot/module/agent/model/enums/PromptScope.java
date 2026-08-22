package io.github.mangomaner.mangobot.module.agent.model.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 提示词作用域：继承链 SESSION → BOT → GLOBAL → resource 出厂模板。
 *
 * <p>Web 仅支持 GLOBAL；群聊/私聊支持全部三级。未编辑的作用域不落行，渲染时自动继承上一级。
 */
@Getter
@RequiredArgsConstructor
public enum PromptScope {
    GLOBAL("GLOBAL", "全局默认"),
    BOT("BOT", "Bot 默认"),
    SESSION("SESSION", "会话级");

    @EnumValue
    private final String code;
    private final String description;

    public static PromptScope fromCode(String code) {
        for (PromptScope scope : values()) {
            if (scope.getCode().equalsIgnoreCase(code)) {
                return scope;
            }
        }
        throw new IllegalArgumentException("Unknown prompt scope: " + code);
    }
}
