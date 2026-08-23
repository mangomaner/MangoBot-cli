package io.github.mangomaner.mangobot.module.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.mangomaner.mangobot.module.agent.core.PromptTemplateLoader;
import io.github.mangomaner.mangobot.module.agent.model.domain.PromptNorm;
import io.github.mangomaner.mangobot.module.agent.model.dto.PromptNormRequest;
import io.github.mangomaner.mangobot.module.agent.model.enums.PromptLayer;
import io.github.mangomaner.mangobot.module.agent.model.enums.PromptScope;
import io.github.mangomaner.mangobot.module.agent.model.enums.SessionSource;
import io.github.mangomaner.mangobot.module.agent.model.vo.PromptEditVO;
import io.github.mangomaner.mangobot.module.agent.model.vo.PromptLayerVO;
import io.github.mangomaner.mangobot.module.agent.service.PromptNormService;
import io.github.mangomaner.mangobot.system.common.ErrorCode;
import io.github.mangomaner.mangobot.system.exception.BusinessException;
import io.github.mangomaner.mangobot.system.mapper.agent.PromptNormMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 提示词规范（L1/L2）Service 实现。
 *
 * <p>继承链：SESSION → BOT → GLOBAL → 出厂模板（group/private）；web 仅 GLOBAL → 出厂模板。
 * "恢复默认"统一为删除行，行不存在即自动继承上一级，无需特判。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromptNormServiceImpl implements PromptNormService {

    /** L1 高危校验关键词：各来源至少需保留其一的协议词，防止清空导致 bot 无法回复 */
    private static final Map<String, List<String>> L1_REQUIRED_KEYWORDS = Map.of(
            SessionSource.GROUP.getSourceKey(), List.of("sendGroupMessage", "sendGroupReply"),
            SessionSource.PRIVATE.getSourceKey(), List.of("sendPrivateMessage", "sendPrivateReply"),
            SessionSource.WEB.getSourceKey(), List.of("输出"));

    private final PromptNormMapper promptNormMapper;
    private final PromptTemplateLoader templateLoader;

    @Override
    public String resolveContent(String botId, SessionSource source, String chatId, PromptLayer layer) {
        return resolve(botId, source, chatId, layer).content();
    }

    @Override
    public PromptEditVO getEditPage(PromptScope scope, String botId, SessionSource source, String chatId) {
        validateScope(scope, source, botId, chatId);
        List<PromptLayerVO> layers = new ArrayList<>(PromptLayer.values().length);
        for (PromptLayer layer : PromptLayer.values()) {
            Resolved resolved = resolve(botId, source, chatId, layer);
            PromptLayerVO vo = PromptLayerVO.builder()
                    .layer(layer.getCode())
                    .title(layer.getDescription())
                    .content(resolved.content())
                    .effectiveScope(resolved.effectiveScope())
                    .customized(exists(scope, source, botId, chatId, layer))
                    .template(layer == PromptLayer.FRAMEWORK ? templateLoader.load(source, layer) : null)
                    .build();
            layers.add(vo);
        }
        return PromptEditVO.builder()
                .scope(scope.getCode())
                .botId(botId)
                .source(source.getSourceKey())
                .chatId(chatId)
                .layers(layers)
                .build();
    }

    @Override
    public void save(PromptNormRequest request) {
        PromptScope scope = parseScope(request.getScope());
        SessionSource source = parseSource(request.getSource());
        PromptLayer layer = parseLayer(request.getLayer());
        validateScope(scope, source, request.getBotId(), request.getChatId());
        String content = request.getContent();
        if (!StringUtils.hasText(content)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "内容不能为空");
        }
        validateFramework(source, layer, content);

        long now = System.currentTimeMillis();
        PromptNorm existing = findByUnique(scope, source, request.getBotId(), request.getChatId(), layer);
        if (existing != null) {
            existing.setContent(content);
            existing.setEnabled(1);
            existing.setUpdatedAt(now);
            promptNormMapper.updateById(existing);
        } else {
            PromptNorm row = new PromptNorm();
            row.setScope(scope.getCode());
            row.setSource(source.getSourceKey());
            row.setBotId(botIdFor(scope, request.getBotId()));
            row.setChatId(chatIdFor(scope, request.getChatId()));
            row.setLayer(layer.getCode());
            row.setContent(content);
            row.setEnabled(1);
            row.setCreatedAt(now);
            row.setUpdatedAt(now);
            promptNormMapper.insert(row);
        }
    }

    @Override
    public void restoreDefault(PromptScope scope, String botId, SessionSource source, String chatId, PromptLayer layer) {
        PromptNorm existing = findByUnique(scope, source, botId, chatId, layer);
        if (existing != null) {
            promptNormMapper.deleteById(existing.getId());
        }
    }

    /** 返回该层实际生效内容与生效来源 */
    private Resolved resolve(String botId, SessionSource source, String chatId, PromptLayer layer) {
        List<PromptScope> chain = source == SessionSource.WEB
                ? List.of(PromptScope.GLOBAL)
                : List.of(PromptScope.SESSION, PromptScope.BOT, PromptScope.GLOBAL);
        for (PromptScope scope : chain) {
            PromptNorm row = findByUnique(scope, source, botId, chatId, layer);
            if (row != null && StringUtils.hasText(row.getContent())) {
                return new Resolved(row.getContent(), scope.getCode());
            }
        }
        String template = templateLoader.load(source, layer);
        return new Resolved(template != null ? template : "", "TEMPLATE");
    }

    /** 按唯一键 (scope, source, bot_id, chat_id, layer) 查询；bot/chat 字段按作用域归一化 */
    private PromptNorm findByUnique(PromptScope scope, SessionSource source,
                                    String botId, String chatId, PromptLayer layer) {
        return promptNormMapper.selectOne(new QueryWrapper<PromptNorm>()
                .eq("scope", scope.getCode())
                .eq("source", source.getSourceKey())
                .eq("bot_id", botIdFor(scope, botId))
                .eq("chat_id", chatIdFor(scope, chatId))
                .eq("layer", layer.getCode())
                .last("LIMIT 1"));
    }

    /** GLOBAL 落空串，其余作用域用实际 botId */
    private static String botIdFor(PromptScope scope, String botId) {
        return scope == PromptScope.GLOBAL ? "" : normalize(botId);
    }

    /** 仅 SESSION 用实际 chatId */
    private static String chatIdFor(PromptScope scope, String chatId) {
        return scope == PromptScope.SESSION ? normalize(chatId) : "";
    }

    private boolean exists(PromptScope scope, SessionSource source,
                           String botId, String chatId, PromptLayer layer) {
        return findByUnique(scope, source, botId, chatId, layer) != null;
    }

    /** 作用域合法性校验：SESSION 需 chatId；非 GLOBAL 需 botId；web 仅 GLOBAL */
    private void validateScope(PromptScope scope, SessionSource source, String botId, String chatId) {
        if (source == SessionSource.WEB && scope != PromptScope.GLOBAL) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "Web 端仅支持全局默认（GLOBAL）作用域");
        }
        if (scope != PromptScope.GLOBAL && !StringUtils.hasText(botId)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "非 GLOBAL 作用域必须指定 botId");
        }
        if (scope == PromptScope.SESSION && !StringUtils.hasText(chatId)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "SESSION 作用域必须指定 chatId");
        }
    }

    /** L1 高危层：必须保留协议关键词，防止误删工具调用协议 */
    private void validateFramework(SessionSource source, PromptLayer layer, String content) {
        if (layer != PromptLayer.FRAMEWORK) {
            return;
        }
        List<String> keywords = L1_REQUIRED_KEYWORDS.get(source.getSourceKey());
        if (keywords != null && keywords.stream().noneMatch(content::contains)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,
                    "框架规范必须包含协议关键词 " + String.join(" 或 ", keywords) + "，请勿删除工具调用协议");
        }
    }

    private PromptScope parseScope(String scope) {
        try {
            return PromptScope.fromCode(scope);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "非法作用域: " + scope);
        }
    }

    private SessionSource parseSource(String source) {
        try {
            return SessionSource.fromKey(source);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "非法来源: " + source);
        }
    }

    private PromptLayer parseLayer(String layer) {
        try {
            return PromptLayer.fromCode(layer);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "非法层: " + layer);
        }
    }

    /** 未用字段统一落空串，保证唯一索引一致（GLOBAL/BOT 无 chatId 等） */
    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private record Resolved(String content, String effectiveScope) {
    }
}
