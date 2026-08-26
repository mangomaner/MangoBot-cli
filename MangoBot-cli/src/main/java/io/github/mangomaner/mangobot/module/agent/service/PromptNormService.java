package io.github.mangomaner.mangobot.module.agent.service;

import io.github.mangomaner.mangobot.module.agent.model.dto.PromptNormRequest;
import io.github.mangomaner.mangobot.module.agent.model.enums.BotType;
import io.github.mangomaner.mangobot.module.agent.model.enums.PromptLayer;
import io.github.mangomaner.mangobot.module.agent.model.enums.PromptScope;
import io.github.mangomaner.mangobot.module.agent.model.enums.SessionSource;
import io.github.mangomaner.mangobot.module.agent.model.vo.PromptEditVO;

/**
 * 提示词规范（L1/L2）Service：三级作用域的读写与继承解析。
 */
public interface PromptNormService {

    /**
     * 按继承链解析某层的最终生效内容。
     *
     * @return 非 null（无任何层级内容时返回空串）。group/private：SESSION→BOT→GLOBAL→出厂模板；
     *         web：GLOBAL→出厂模板。
     */
    String resolveContent(String botId, SessionSource source, String chatId, PromptLayer layer);

    /** 编辑页聚合：返回请求作用域下三层（framework/persona/behavior）的编辑信息。 */
    PromptEditVO getEditPage(PromptScope scope, String botId, SessionSource source, String chatId);

    /** 编辑页聚合（按指定 Bot 类型预览模板地板）：overrideType 非 null 时仅影响模板地板预览，不写库。 */
    PromptEditVO getEditPage(PromptScope scope, String botId, SessionSource source, String chatId, BotType overrideType);

    /** 保存某层内容（按作用域 upsert）。 */
    void save(PromptNormRequest request);

    /** 恢复默认：删除请求作用域下的行（自动回退到上一级）。 */
    void restoreDefault(PromptScope scope, String botId, SessionSource source, String chatId, PromptLayer layer);
}
