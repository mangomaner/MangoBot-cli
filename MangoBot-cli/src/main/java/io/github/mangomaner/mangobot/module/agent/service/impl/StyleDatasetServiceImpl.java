package io.github.mangomaner.mangobot.module.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mangomaner.mangobot.module.agent.model.dataset.StyleMessage;
import io.github.mangomaner.mangobot.module.agent.model.dataset.StyleRole;
import io.github.mangomaner.mangobot.module.agent.model.domain.StyleDataset;
import io.github.mangomaner.mangobot.module.agent.model.domain.StyleInjection;
import io.github.mangomaner.mangobot.module.agent.model.dto.StyleDatasetImportRequest;
import io.github.mangomaner.mangobot.module.agent.model.dto.StyleDatasetRequest;
import io.github.mangomaner.mangobot.module.agent.model.enums.SessionSource;
import io.github.mangomaner.mangobot.module.agent.model.vo.StyleDatasetDetailVO;
import io.github.mangomaner.mangobot.module.agent.model.vo.StyleDatasetVO;
import io.github.mangomaner.mangobot.module.agent.service.StyleDatasetService;
import io.github.mangomaner.mangobot.system.common.ErrorCode;
import io.github.mangomaner.mangobot.system.exception.BusinessException;
import io.github.mangomaner.mangobot.system.mapper.agent.StyleDatasetMapper;
import io.github.mangomaner.mangobot.system.mapper.agent.StyleInjectionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 风格数据集（L3）Service 实现。
 *
 * <p>roles/messages 以 JSON 字符串内嵌于 style_dataset 表，读写统一经本类编解码；
 * 删除数据集时级联清理引用它的注入关联，避免悬挂指针。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StyleDatasetServiceImpl implements StyleDatasetService {

    private final StyleDatasetMapper styleDatasetMapper;
    private final StyleInjectionMapper styleInjectionMapper;
    private final ObjectMapper objectMapper;

    @Override
    public List<StyleDatasetVO> listBySource(SessionSource source) {
        List<StyleDataset> datasets = styleDatasetMapper.selectList(
                new QueryWrapper<StyleDataset>()
                        .eq("source", source.getSourceKey())
                        .orderByDesc("updated_at"));
        return datasets.stream().map(this::toListVO).toList();
    }

    @Override
    public StyleDatasetDetailVO getDetail(Long id) {
        return toDetailVO(requireDataset(id));
    }

    @Override
    public StyleDatasetDetailVO create(StyleDatasetRequest request) {
        validateRequest(request);
        StyleDataset dataset = new StyleDataset();
        dataset.setName(request.getName());
        dataset.setDescription(request.getDescription());
        dataset.setSource(request.getSource());
        dataset.setCreateType("created");
        dataset.setRoles(writeJson(request.getRoles()));
        dataset.setMessages(writeJson(request.getMessages()));
        fillTimestamps(dataset, true);
        styleDatasetMapper.insert(dataset);
        return toDetailVO(dataset);
    }

    @Override
    public StyleDatasetDetailVO update(StyleDatasetRequest request) {
        if (request.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "编辑数据集必须指定 id");
        }
        validateRequest(request);
        StyleDataset dataset = requireDataset(request.getId());
        dataset.setName(request.getName());
        dataset.setDescription(request.getDescription());
        dataset.setSource(request.getSource());
        dataset.setRoles(writeJson(request.getRoles()));
        dataset.setMessages(writeJson(request.getMessages()));
        dataset.setUpdatedAt(System.currentTimeMillis());
        styleDatasetMapper.updateById(dataset);
        return toDetailVO(dataset);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireDataset(id);
        styleInjectionMapper.delete(new QueryWrapper<StyleInjection>().eq("dataset_id", id));
        styleDatasetMapper.deleteById(id);
    }

    @Override
    public StyleDatasetDetailVO importFromSession(StyleDatasetImportRequest request) {
        if (!StringUtils.hasText(request.getName())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "数据集名称不能为空");
        }
        SessionSource source = parseSource(request.getSource());
        List<StyleDatasetImportRequest.ImportMessage> messages = request.getMessages();
        if (messages == null || messages.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请至少选择一条消息");
        }

        // 按发送者归并角色（保持首次出现顺序），统一视为普通角色
        Set<String> roleNames = new LinkedHashSet<>();
        for (StyleDatasetImportRequest.ImportMessage msg : messages) {
            if (StringUtils.hasText(msg.getSenderName())) {
                roleNames.add(msg.getSenderName());
            }
        }
        if (roleNames.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "所选消息缺少发送者信息，无法生成角色");
        }

        List<StyleRole> roles = roleNames.stream().map(StyleRole::new).toList();
        List<StyleMessage> styleMessages = messages.stream()
                .filter(msg -> StringUtils.hasText(msg.getText()) && roleNames.contains(msg.getSenderName()))
                .map(msg -> new StyleMessage(msg.getSenderName(), msg.getText()))
                .toList();

        StyleDataset dataset = new StyleDataset();
        dataset.setName(request.getName());
        dataset.setDescription(request.getDescription());
        dataset.setSource(source.getSourceKey());
        dataset.setCreateType("imported");
        dataset.setSourceSessionId(request.getSourceSessionId());
        dataset.setRoles(writeJson(roles));
        dataset.setMessages(writeJson(styleMessages));
        fillTimestamps(dataset, true);
        styleDatasetMapper.insert(dataset);
        return toDetailVO(dataset);
    }

    private StyleDataset requireDataset(Long id) {
        StyleDataset dataset = styleDatasetMapper.selectById(id);
        if (dataset == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "风格数据集不存在: " + id);
        }
        return dataset;
    }

    private void validateRequest(StyleDatasetRequest request) {
        if (!StringUtils.hasText(request.getName())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "数据集名称不能为空");
        }
        parseSource(request.getSource());
        if (request.getRoles() == null || request.getRoles().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "至少需要一个角色");
        }
        if (request.getMessages() == null || request.getMessages().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "至少需要一条消息");
        }
        List<String> roleNames = request.getRoles().stream().map(StyleRole::name).toList();
        for (StyleMessage message : request.getMessages()) {
            if (!roleNames.contains(message.roleName())) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "消息角色不存在: " + message.roleName());
            }
        }
    }

    private StyleDatasetVO toListVO(StyleDataset dataset) {
        return StyleDatasetVO.builder()
                .id(dataset.getId())
                .name(dataset.getName())
                .description(dataset.getDescription())
                .source(dataset.getSource())
                .createType(dataset.getCreateType())
                .sourceSessionId(dataset.getSourceSessionId())
                .roleCount(parseRoles(dataset.getRoles()).size())
                .messageCount(parseMessages(dataset.getMessages()).size())
                .createdAt(dataset.getCreatedAt())
                .updatedAt(dataset.getUpdatedAt())
                .build();
    }

    private StyleDatasetDetailVO toDetailVO(StyleDataset dataset) {
        return StyleDatasetDetailVO.builder()
                .id(dataset.getId())
                .name(dataset.getName())
                .description(dataset.getDescription())
                .source(dataset.getSource())
                .createType(dataset.getCreateType())
                .sourceSessionId(dataset.getSourceSessionId())
                .roles(parseRoles(dataset.getRoles()))
                .messages(parseMessages(dataset.getMessages()))
                .createdAt(dataset.getCreatedAt())
                .updatedAt(dataset.getUpdatedAt())
                .build();
    }

    private List<StyleRole> parseRoles(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<List<StyleRole>>() {});
        } catch (JsonProcessingException e) {
            log.error("Failed to parse style_dataset roles: {}", json, e);
            return List.of();
        }
    }

    private List<StyleMessage> parseMessages(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<List<StyleMessage>>() {});
        } catch (JsonProcessingException e) {
            log.error("Failed to parse style_dataset messages: {}", json, e);
            return List.of();
        }
    }

    private String writeJson(List<?> list) {
        try {
            return objectMapper.writeValueAsString(list != null ? list : List.of());
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "数据集序列化失败");
        }
    }

    private void fillTimestamps(StyleDataset dataset, boolean create) {
        long now = System.currentTimeMillis();
        if (create) {
            dataset.setCreatedAt(now);
        }
        dataset.setUpdatedAt(now);
    }

    private SessionSource parseSource(String source) {
        try {
            return SessionSource.fromKey(source);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "非法来源: " + source);
        }
    }
}
