package io.github.mangomaner.mangobot.module.agent.service;

import com.baomidou.mybatisplus.extension.service.IService;
import io.github.mangomaner.mangobot.module.agent.model.domain.ChatSession;
import io.github.mangomaner.mangobot.module.agent.model.dto.CreateChatSessionRequest;
import io.github.mangomaner.mangobot.module.agent.model.dto.UpdateChatSessionRequest;
import io.github.mangomaner.mangobot.module.agent.model.enums.SessionSource;
import io.github.mangomaner.mangobot.module.agent.model.vo.ChatSessionVO;

import java.util.List;

/**
 * 对话会话 Service
 */
public interface ChatSessionService extends IService<ChatSession> {

    /**
     * 创建对话会话
     */
    ChatSessionVO createSession(CreateChatSessionRequest request);

    /**
     * 根据ID获取会话详情
     */
    ChatSessionVO getSessionById(Integer id);

    /**
     * 获取指定 Bot 下的所有会话列表
     */
    List<ChatSessionVO> listSessionsByBotId(String botId);

    /**
     * 更新会话信息
     */
    ChatSessionVO updateSession(Integer id, UpdateChatSessionRequest request);

    /**
     * 删除会话及其所有消息
     */
    void deleteSession(Integer id);

    /**
     * 根据 botId / chatId / source 获取会话（不存在时自动创建）
     */
    ChatSessionVO getSessionByBotIdAndChatId(String botId, String chatId, SessionSource source);

    /**
     * 根据 botId / chatId / source 获取会话（不存在时返回 null，不创建）
     */
    ChatSessionVO getSessionByBotIdAndChatIdOrNull(String botId, String chatId, SessionSource source);
}
