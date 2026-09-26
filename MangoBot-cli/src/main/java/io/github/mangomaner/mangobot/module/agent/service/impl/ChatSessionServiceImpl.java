package io.github.mangomaner.mangobot.module.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import io.github.mangomaner.mangobot.module.agent.model.domain.ChatMessageWeb;
import io.github.mangomaner.mangobot.module.agent.model.domain.ChatSession;
import io.github.mangomaner.mangobot.module.agent.model.dto.CreateChatSessionRequest;
import io.github.mangomaner.mangobot.module.agent.model.dto.UpdateChatSessionRequest;
import io.github.mangomaner.mangobot.module.agent.model.enums.SessionSource;
import io.github.mangomaner.mangobot.module.agent.model.vo.ChatSessionVO;
import io.github.mangomaner.mangobot.module.agent.service.ChatMessageWebService;
import io.github.mangomaner.mangobot.module.agent.service.ChatSessionService;
import io.github.mangomaner.mangobot.system.common.ErrorCode;
import io.github.mangomaner.mangobot.system.exception.BusinessException;
import io.github.mangomaner.mangobot.system.mapper.agent.ChatSessionMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 对话会话 Service 实现
 *
 * <p>会话表只是 UI 查询投影：真正的对话上下文活在 claude 子进程与它自己的转录里
 * （见 {@code ChatSession} 的类注释）；会话以 {@code (bot_id, source, chat_id)} 为业务键。
 */
@Slf4j
@Service
public class ChatSessionServiceImpl extends ServiceImpl<ChatSessionMapper, ChatSession>
        implements ChatSessionService {

    @Resource
    private ChatMessageWebService chatMessageService;

    /** 注入自身代理对象，解决 @Transactional 自调用失效问题 */
    @Lazy
    @Resource
    private ChatSessionService self;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChatSessionVO createSession(CreateChatSessionRequest request) {
        if (!StringUtils.hasText(request.getTitle())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "会话标题不能为空");
        }

        ChatSession session = new ChatSession();
        session.setTitle(request.getTitle().trim());
        session.setBotId(request.getBotId());
        session.setChatId(request.getChatId());
        session.setSource(request.getSource() != null ? request.getSource() : SessionSource.WEB);
        session.setCreateTime(new Date());
        session.setUpdateTime(new Date());

        boolean saved = this.save(session);
        if (!saved) {
            log.error("创建会话失败: {}", request.getTitle());
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "创建会话失败");
        }
        log.info("创建会话成功，sessionId: {}, title: {}", session.getId(), session.getTitle());
        return convertToVO(session);
    }

    @Override
    public ChatSessionVO getSessionById(Integer id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "会话ID不能为空");
        }
        ChatSession session = this.getById(id);
        if (session == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "会话不存在");
        }
        return convertToVO(session);
    }

    @Override
    public List<ChatSessionVO> listSessionsByBotId(String botId) {
        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<>();
        if (!StringUtils.hasText(botId)) {
            // 网页端会话不绑定任何 Bot（bot_id 为空），空 botId 时列出这些会话
            wrapper.isNull(ChatSession::getBotId);
        } else {
            wrapper.eq(ChatSession::getBotId, botId);
        }
        wrapper.orderByDesc(ChatSession::getUpdateTime);
        List<ChatSession> sessions = this.list(wrapper);
        return sessions.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChatSessionVO updateSession(Integer id, UpdateChatSessionRequest request) {
        if (id == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "会话ID不能为空");
        }
        ChatSession session = this.getById(id);
        if (session == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "会话不存在");
        }

        boolean needUpdate = false;
        if (StringUtils.hasText(request.getTitle())) {
            session.setTitle(request.getTitle().trim());
            needUpdate = true;
        }
        if (needUpdate) {
            session.setUpdateTime(new Date());
            boolean updated = this.updateById(session);
            if (!updated) {
                log.error("更新会话失败，sessionId: {}", id);
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "更新会话失败");
            }
            log.info("更新会话成功，sessionId: {}", id);
        }
        return convertToVO(session);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSession(Integer id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "会话ID不能为空");
        }
        ChatSession session = this.getById(id);
        if (session == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "会话不存在");
        }
        chatMessageService.deleteMessagesBySessionId(id);
        boolean removed = this.removeById(id);
        if (!removed) {
            log.error("删除会话失败，sessionId: {}", id);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "删除会话失败");
        }
        log.info("删除会话成功，sessionId: {}", id);
    }

    @Override
    public ChatSessionVO getSessionByBotIdAndChatId(String botId, String chatId, SessionSource source) {
        ChatSessionVO existing = getSessionByBotIdAndChatIdOrNull(botId, chatId, source);
        if (existing != null) {
            return existing;
        }
        CreateChatSessionRequest request = CreateChatSessionRequest.builder()
                .title((source == SessionSource.GROUP ? "群聊" : "私聊") + chatId)
                .botId(botId)
                .chatId(chatId)
                .source(source)
                .build();
        return self.createSession(request);
    }

    @Override
    public ChatSessionVO getSessionByBotIdAndChatIdOrNull(String botId, String chatId, SessionSource source) {
        if (botId == null || chatId == null || source == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "botId/chatId/source 不能为空");
        }
        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatSession::getBotId, botId)
                .eq(ChatSession::getChatId, chatId)
                .eq(ChatSession::getSource, source);
        ChatSession session = this.getOne(wrapper);
        return session == null ? null : convertToVO(session);
    }

    private ChatSessionVO convertToVO(ChatSession session) {
        if (session == null) {
            return null;
        }
        ChatSessionVO vo = new ChatSessionVO();
        BeanUtils.copyProperties(session, vo);

        long messageCount = chatMessageService.count(
                new LambdaQueryWrapper<ChatMessageWeb>()
                        .eq(ChatMessageWeb::getSessionId, session.getId()));
        vo.setMessageCount(messageCount);
        return vo;
    }
}
