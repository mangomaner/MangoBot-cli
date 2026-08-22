package io.github.mangomaner.mangobot.module.agent.service;

import io.github.mangomaner.mangobot.module.agent.model.dto.StyleDatasetImportRequest;
import io.github.mangomaner.mangobot.module.agent.model.dto.StyleDatasetRequest;
import io.github.mangomaner.mangobot.module.agent.model.enums.SessionSource;
import io.github.mangomaner.mangobot.module.agent.model.vo.StyleDatasetDetailVO;
import io.github.mangomaner.mangobot.module.agent.model.vo.StyleDatasetVO;

import java.util.List;

/**
 * 风格数据集（L3）Service：公共数据中心的 CRUD 与从会话导入。
 */
public interface StyleDatasetService {

    /** 按来源列出数据集（不含 roles/messages 明细）。 */
    List<StyleDatasetVO> listBySource(SessionSource source);

    /** 数据集详情（含角色与消息）。 */
    StyleDatasetDetailVO getDetail(Long id);

    /** 人工创建。 */
    StyleDatasetDetailVO create(StyleDatasetRequest request);

    /** 编辑（名称/描述/角色/消息）。 */
    StyleDatasetDetailVO update(StyleDatasetRequest request);

    /** 删除数据集并级联清理其注入关联。 */
    void delete(Long id);

    /** 从会话消息导入：按发送者自动归并生成角色。 */
    StyleDatasetDetailVO importFromSession(StyleDatasetImportRequest request);
}
