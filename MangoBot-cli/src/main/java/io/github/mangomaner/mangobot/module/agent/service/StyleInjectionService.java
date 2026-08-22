package io.github.mangomaner.mangobot.module.agent.service;

import io.github.mangomaner.mangobot.module.agent.model.dto.StyleInjectionRequest;
import io.github.mangomaner.mangobot.module.agent.model.vo.StyleInjectionVO;

/**
 * 风格数据集注入（L3 关联）Service：作用域 → 数据集的解析与维护。
 */
public interface StyleInjectionService {

    /**
     * 解析当前生效的注入集合。
     *
     * @return datasets 为生效列表；无注入时为空列表。
     */
    StyleInjectionVO getEffective(String botId, String source, String chatId);

    /** 向指定作用域追加注入一个数据集（幂等，重复添加忽略）。 */
    StyleInjectionVO addInjection(StyleInjectionRequest request);

    /** 移除指定作用域下的某个数据集注入（恢复继承/不注入）。 */
    StyleInjectionVO removeInjection(StyleInjectionRequest request);
}
