package io.github.mangomaner.mangobot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mangomaner.mangobot.mapper.PrivateMessagesMapper;
import io.github.mangomaner.mangobot.model.domain.PrivateMessages;
import io.github.mangomaner.mangobot.model.dto.message.*;
import io.github.mangomaner.mangobot.model.onebot.event.message.PrivateMessageEvent;
import io.github.mangomaner.mangobot.service.PrivateMessagesService;
import io.github.mangomaner.mangobot.utils.MessageParser;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

/**
* @author mangoman
* @description 针对表【private_messages】的数据库操作Service实现
* @createDate 2026-01-17 18:03:57
*/
@Service
public class PrivateMessagesServiceImpl extends ServiceImpl<PrivateMessagesMapper, PrivateMessages>
    implements PrivateMessagesService {

    private static final int PAGE_SIZE = 25;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<PrivateMessages> getLatestMessages(QueryLatestMessagesRequest request) {
        LambdaQueryWrapper<PrivateMessages> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PrivateMessages::getBotId, request.getBotId())
                .eq(PrivateMessages::getFriendId, request.getTargetId())
                .orderByDesc(PrivateMessages::getMessageTime)
                .last("LIMIT " + PAGE_SIZE);
        return this.list(wrapper);
    }

    @Override
    public List<PrivateMessages> getMessagesByMessageId(QueryMessagesByMessageIdRequest request) {
        LambdaQueryWrapper<PrivateMessages> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PrivateMessages::getBotId, request.getBotId())
                .eq(PrivateMessages::getFriendId, request.getTargetId())
                .lt(PrivateMessages::getMessageTime, 
                    this.getOne(new LambdaQueryWrapper<PrivateMessages>()
                            .eq(PrivateMessages::getMessageId, request.getMessageId()))
                            .getMessageTime())
                .orderByDesc(PrivateMessages::getMessageTime)
                .last("LIMIT " + PAGE_SIZE);
        return this.list(wrapper);
    }

    @Override
    public List<PrivateMessages> getMessagesBySender(QueryMessagesBySenderRequest request) {
        LambdaQueryWrapper<PrivateMessages> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PrivateMessages::getBotId, request.getBotId())
                .eq(PrivateMessages::getSenderId, request.getSenderId())
                .orderByDesc(PrivateMessages::getMessageTime)
                .last("LIMIT " + PAGE_SIZE);
        return this.list(wrapper);
    }

    @Override
    public List<PrivateMessages> searchMessages(SearchMessagesRequest request) {
        LambdaQueryWrapper<PrivateMessages> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PrivateMessages::getBotId, request.getBotId())
                .eq(PrivateMessages::getFriendId, request.getTargetId())
                .like(PrivateMessages::getParseMessage, request.getKeyword())
                .orderByDesc(PrivateMessages::getMessageTime);
        return this.list(wrapper);
    }

    @Override
    public PrivateMessages getMessageById(Integer id) {
        return this.getById(id);
    }

    @Override
    public PrivateMessages addPrivateMessage(PrivateMessageEvent event) {
        try {
            PrivateMessages privateMessages = new PrivateMessages();
            privateMessages.setBotId(event.getSelfId());
            privateMessages.setFriendId(event.getUserId());
            privateMessages.setMessageId(event.getMessageId());
            privateMessages.setSenderId(event.getUserId());
            privateMessages.setMessageSegments(objectMapper.writeValueAsString(event.getMessage()));
            privateMessages.setMessageTime(event.getTime() * 1000L);
            privateMessages.setParseMessage(event.getParsedMessage());
            this.save(privateMessages);
            return privateMessages;
        } catch (Exception e) {
            throw new RuntimeException("Failed to add private message", e);
        }
    }

    @Override
    public Boolean deleteMessage(Integer id) {
        PrivateMessages message = this.getById(id);
        if (message == null) {
            return false;
        }
        message.setIsDelete(1);
        return this.updateById(message);
    }

    @Override
    public Boolean updateMessage(UpdateMessageRequest request) {
        PrivateMessages message = this.getById(request.getId());
        if (message == null) {
            return false;
        }
        if (request.getMessageSegments() != null) {
            message.setMessageSegments(request.getMessageSegments());
        }
        if (request.getParseMessage() != null) {
            message.setParseMessage(request.getParseMessage());
        }
        return this.updateById(message);
    }
}




