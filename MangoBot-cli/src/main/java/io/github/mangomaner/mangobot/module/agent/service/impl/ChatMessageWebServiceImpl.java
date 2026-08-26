package io.github.mangomaner.mangobot.module.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import io.github.mangomaner.mangobot.module.agent.model.domain.ChatMessageWeb;
import io.github.mangomaner.mangobot.module.agent.model.domain.ChatSession;
import io.github.mangomaner.mangobot.module.agent.model.dto.ChatMessageWebRequest;
import io.github.mangomaner.mangobot.module.agent.model.vo.ChatMessageWebVO;
import io.github.mangomaner.mangobot.module.agent.service.ChatMessageWebService;
import io.github.mangomaner.mangobot.system.common.ErrorCode;
import io.github.mangomaner.mangobot.system.exception.BusinessException;
import io.github.mangomaner.mangobot.system.mapper.agent.ChatMessageWebMapper;
import io.github.mangomaner.mangobot.system.mapper.agent.ChatSessionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
* @author mangoman
* @description 针对表【chat_message_web】的数据库操作Service实现
* @createDate 2026-03-14 23:33:58
*/
@Service
@Slf4j
@RequiredArgsConstructor
public class ChatMessageWebServiceImpl extends ServiceImpl<ChatMessageWebMapper, ChatMessageWeb>
    implements ChatMessageWebService{
    /**
     * 直接注入 ChatSessionMapper 而非 ChatSessionService，避免循环依赖
     */
    private final ChatSessionMapper chatSessionMapper;

    /**
     * 角色常量：用户
     */
    private static final String ROLE_USER = "user";

    /**
     * 角色常量：AI助手
     */
    private static final String ROLE_ASSISTANT = "assistant";

    /**
     * 角色常量：系统
     */
    private static final String ROLE_SYSTEM = "system";

    /**
     * 分页默认条数
     */
    private static final int DEFAULT_PAGE_SIZE = 30;

    /**
     * 分页条数上限（防止一次性拉取过多）
     */
    private static final int MAX_PAGE_SIZE = 100;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChatMessageWebVO createMessage(ChatMessageWebRequest request) {
        // 参数校验
        if (request.getSessionId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "会话ID不能为空");
        }
        if (!StringUtils.hasText(request.getContent())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "消息内容不能为空");
        }

        // 校验会话是否存在（直接使用 Mapper 查询，避免循环依赖）
        ChatSession session = chatSessionMapper.selectById(request.getSessionId());
        if (session == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "会话不存在");
        }

        // 校验角色
        String role = request.getRole();
        if (!StringUtils.hasText(role)) {
            role = ROLE_USER;
        }
        if (!ROLE_USER.equals(role) && !ROLE_ASSISTANT.equals(role) && !ROLE_SYSTEM.equals(role)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "无效的角色类型");
        }

        // 创建消息实体
        ChatMessageWeb message = new ChatMessageWeb();
        message.setSessionId(request.getSessionId());
        message.setRole(role);
        message.setContent(request.getContent().trim());
        message.setCreateTime(new Date());

        // 保存到数据库
        boolean saved = this.save(message);
        if (!saved) {
            log.error("创建消息失败，sessionId: {}", request.getSessionId());
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "创建消息失败");
        }

        log.info("创建消息成功，messageId: {}, sessionId: {}, role: {}",
                message.getId(), message.getSessionId(), message.getRole());
        return convertToVO(message);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChatMessageWebVO createAssistantMessage(Integer sessionId, String content, String metadata) {
        if (sessionId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "会话ID不能为空");
        }
        if (!StringUtils.hasText(content)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "消息内容不能为空");
        }

        // 校验会话是否存在（直接使用 Mapper 查询，避免循环依赖）
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "会话不存在");
        }

        // 创建AI回复消息实体
        ChatMessageWeb message = new ChatMessageWeb();
        message.setSessionId(sessionId);
        message.setRole(ROLE_ASSISTANT);
        message.setContent(content.trim());
        message.setCreateTime(new Date());

        // 保存到数据库
        boolean saved = this.save(message);
        if (!saved) {
            log.error("创建AI消息失败，sessionId: {}", sessionId);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "创建AI消息失败");
        }

        log.info("创建AI消息成功，messageId: {}, sessionId: {}", message.getId(), sessionId);
        return convertToVO(message);
    }

    @Override
    public ChatMessageWebVO getMessageById(Integer id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "消息ID不能为空");
        }

        ChatMessageWeb message = this.getById(id);
        if (message == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "消息不存在");
        }

        return convertToVO(message);
    }

    @Override
    public List<ChatMessageWebVO> listMessagesBySessionId(Integer sessionId) {
        if (sessionId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "会话ID不能为空");
        }

        // 查询会话下的所有消息，按时间正序
        LambdaQueryWrapper<ChatMessageWeb> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatMessageWeb::getSessionId, sessionId)
                .orderByAsc(ChatMessageWeb::getCreateTime);

        List<ChatMessageWeb> messages = this.list(wrapper);

        return messages.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    @Override
    public List<ChatMessageWebVO> listMessagesBySessionId(Integer sessionId, Integer beforeId, Integer limit) {
        if (sessionId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "会话ID不能为空");
        }

        int size = limit == null ? DEFAULT_PAGE_SIZE : Math.max(1, Math.min(limit, MAX_PAGE_SIZE));
        LambdaQueryWrapper<ChatMessageWeb> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatMessageWeb::getSessionId, sessionId);
        if (beforeId != null) {
            wrapper.lt(ChatMessageWeb::getId, beforeId);
        }
        // id 自增且唯一，作为翻页游标最稳定；DESC 取最近一页后反转回正序
        wrapper.orderByDesc(ChatMessageWeb::getId)
                .last("LIMIT " + size);

        List<ChatMessageWeb> messages = this.list(wrapper);
        Collections.reverse(messages);
        return messages.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    @Override
    public List<ChatMessageWebVO> listMessagesBySessionIdAndRole(Integer sessionId, String role) {
        if (sessionId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "会话ID不能为空");
        }
        if (!StringUtils.hasText(role)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "角色不能为空");
        }

        // 查询会话下指定角色的消息
        LambdaQueryWrapper<ChatMessageWeb> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatMessageWeb::getSessionId, sessionId)
                .eq(ChatMessageWeb::getRole, role)
                .orderByAsc(ChatMessageWeb::getCreateTime);

        List<ChatMessageWeb> messages = this.list(wrapper);

        return messages.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteMessagesBySessionId(Integer sessionId) {
        if (sessionId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "会话ID不能为空");
        }

        // 删除会话下的所有消息
        LambdaQueryWrapper<ChatMessageWeb> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatMessageWeb::getSessionId, sessionId);

        boolean removed = this.remove(wrapper);
        log.info("删除会话消息完成，sessionId: {}, 结果: {}", sessionId, removed);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteMessage(Integer id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "消息ID不能为空");
        }

        ChatMessageWeb message = this.getById(id);
        if (message == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "消息不存在");
        }

        boolean removed = this.removeById(id);
        if (!removed) {
            log.error("删除消息失败，messageId: {}", id);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "删除消息失败");
        }

        log.info("删除消息成功，messageId: {}", id);
    }

    /**
     * 将实体转换为视图对象
     *
     * @param message 消息实体
     * @return 消息视图对象
     */
    private ChatMessageWebVO convertToVO(ChatMessageWeb message) {
        if (message == null) {
            return null;
        }

        ChatMessageWebVO vo = new ChatMessageWebVO();
        BeanUtils.copyProperties(message, vo);
        return vo;
    }
}




