package io.github.mangomaner.mangobot.module.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.mangomaner.mangobot.module.agent.model.domain.StyleDataset;
import io.github.mangomaner.mangobot.module.agent.model.domain.StyleInjection;
import io.github.mangomaner.mangobot.module.agent.model.dto.StyleInjectionRequest;
import io.github.mangomaner.mangobot.module.agent.model.enums.PromptScope;
import io.github.mangomaner.mangobot.module.agent.model.enums.SessionSource;
import io.github.mangomaner.mangobot.module.agent.model.vo.StyleDatasetRefVO;
import io.github.mangomaner.mangobot.module.agent.model.vo.StyleInjectionVO;
import io.github.mangomaner.mangobot.module.agent.service.StyleInjectionService;
import io.github.mangomaner.mangobot.system.common.ErrorCode;
import io.github.mangomaner.mangobot.system.exception.BusinessException;
import io.github.mangomaner.mangobot.system.mapper.agent.StyleDatasetMapper;
import io.github.mangomaner.mangobot.system.mapper.agent.StyleInjectionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 风格数据集注入（L3 关联）Service 实现。
 *
 * <p>解析链：SESSION → BOT → GLOBAL → 不注入；web 不支持风格注入（source 仅 group/private）。
 * 一作用域可注入多个数据集（唯一约束含 dataset_id），添加即追加、移除按数据集删除。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StyleInjectionServiceImpl implements StyleInjectionService {

    private final StyleInjectionMapper styleInjectionMapper;
    private final StyleDatasetMapper styleDatasetMapper;

    @Override
    public StyleInjectionVO getEffective(String botId, String source, String chatId) {
        SessionSource validated = validateSource(source);
        List<PromptScope> chain = List.of(PromptScope.SESSION, PromptScope.BOT, PromptScope.GLOBAL);
        for (PromptScope scope : chain) {
            List<StyleInjection> rows = listByScope(scope, validated, botId, chatId);
            if (rows.isEmpty()) {
                continue;
            }
            List<StyleDatasetRefVO> datasets = rows.stream()
                    .map(row -> toRef(row, scope.getCode()))
                    .filter(ref -> ref != null)
                    .toList();
            if (datasets.isEmpty()) {
                continue;
            }
            return StyleInjectionVO.builder()
                    .scope(scope.getCode())
                    .source(validated.getSourceKey())
                    .botId(botId)
                    .chatId(chatId)
                    .datasets(datasets)
                    .build();
        }
        return StyleInjectionVO.builder()
                .source(validated.getSourceKey())
                .botId(botId)
                .chatId(chatId)
                .datasets(List.of())
                .build();
    }

    @Override
    public StyleInjectionVO addInjection(StyleInjectionRequest request) {
        PromptScope scope = parseScope(request.getScope());
        SessionSource validated = validateSource(request.getSource());
        validateRequest(scope, request);
        StyleDataset dataset = styleDatasetMapper.selectById(request.getDatasetId());
        if (dataset == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "风格数据集不存在: " + request.getDatasetId());
        }
        if (!dataset.getSource().equals(request.getSource())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,
                    "数据集来源不匹配：该数据集属于 " + dataset.getSource() + "，请选择对应来源的数据集");
        }

        if (findByUnique(scope, validated, request.getBotId(), request.getChatId(), request.getDatasetId()) == null) {
            long now = System.currentTimeMillis();
            StyleInjection row = new StyleInjection();
            row.setScope(scope.getCode());
            row.setSource(validated.getSourceKey());
            row.setBotId(botIdFor(scope, request.getBotId()));
            row.setChatId(chatIdFor(scope, request.getChatId()));
            row.setDatasetId(request.getDatasetId());
            row.setCreatedAt(now);
            row.setUpdatedAt(now);
            styleInjectionMapper.insert(row);
        }
        return getEffective(request.getBotId(), request.getSource(), request.getChatId());
    }

    @Override
    public StyleInjectionVO removeInjection(StyleInjectionRequest request) {
        PromptScope scope = parseScope(request.getScope());
        SessionSource validated = validateSource(request.getSource());
        validateRequest(scope, request);
        StyleInjection existing = findByUnique(scope, validated, request.getBotId(), request.getChatId(), request.getDatasetId());
        if (existing != null) {
            styleInjectionMapper.deleteById(existing.getId());
        }
        return getEffective(request.getBotId(), request.getSource(), request.getChatId());
    }

    private StyleDatasetRefVO toRef(StyleInjection row, String effectiveScope) {
        StyleDataset dataset = styleDatasetMapper.selectById(row.getDatasetId());
        if (dataset == null) {
            return null;
        }
        return StyleDatasetRefVO.builder()
                .datasetId(dataset.getId())
                .datasetName(dataset.getName())
                .effectiveScope(effectiveScope)
                .build();
    }

    private List<StyleInjection> listByScope(PromptScope scope, SessionSource source, String botId, String chatId) {
        return styleInjectionMapper.selectList(new QueryWrapper<StyleInjection>()
                .eq("scope", scope.getCode())
                .eq("source", source.getSourceKey())
                .eq("bot_id", botIdFor(scope, botId))
                .eq("chat_id", chatIdFor(scope, chatId)));
    }

    private StyleInjection findByUnique(PromptScope scope, SessionSource source, String botId, String chatId, Long datasetId) {
        return styleInjectionMapper.selectOne(new QueryWrapper<StyleInjection>()
                .eq("scope", scope.getCode())
                .eq("source", source.getSourceKey())
                .eq("bot_id", botIdFor(scope, botId))
                .eq("chat_id", chatIdFor(scope, chatId))
                .eq("dataset_id", datasetId)
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

    private void validateRequest(PromptScope scope, StyleInjectionRequest request) {
        if (request.getDatasetId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "必须指定数据集 ID");
        }
        if (scope != PromptScope.GLOBAL && !StringUtils.hasText(request.getBotId())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "非 GLOBAL 作用域必须指定 botId");
        }
        if (scope == PromptScope.SESSION && !StringUtils.hasText(request.getChatId())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "SESSION 作用域必须指定 chatId");
        }
    }

    /** 风格注入仅支持群聊/私聊；web 直接拒绝 */
    private SessionSource validateSource(String source) {
        SessionSource parsed = parseSource(source);
        if (parsed == SessionSource.WEB) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "风格数据集注入仅支持群聊/私聊");
        }
        return parsed;
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

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
