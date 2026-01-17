package io.github.mangomaner.mangobot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mangomaner.mangobot.model.domain.GroupMessages;
import io.github.mangomaner.mangobot.model.dto.message.QueryLatestMessagesRequest;
import io.github.mangomaner.mangobot.model.dto.message.QueryMessagesByMessageIdRequest;
import io.github.mangomaner.mangobot.model.dto.message.QueryMessagesBySenderRequest;
import io.github.mangomaner.mangobot.model.dto.message.SearchMessagesRequest;
import io.github.mangomaner.mangobot.model.dto.message.UpdateMessageRequest;
import io.github.mangomaner.mangobot.model.onebot.event.message.GroupMessageEvent;
import io.github.mangomaner.mangobot.service.GroupMessagesService;
import io.github.mangomaner.mangobot.mapper.GroupMessagesMapper;
import io.github.mangomaner.mangobot.utils.MessageParser;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

/**
* @author mangoman
* @description 针对表【group_messages】的数据库操作Service实现
* @createDate 2026-01-17 18:03:14
*/
@Service
public class GroupMessagesServiceImpl extends ServiceImpl<GroupMessagesMapper, GroupMessages>
    implements GroupMessagesService{

    private static final int PAGE_SIZE = 25;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<GroupMessages> getLatestMessages(QueryLatestMessagesRequest request) {
        LambdaQueryWrapper<GroupMessages> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(GroupMessages::getBotId, request.getBotId())
                .eq(GroupMessages::getGroupId, request.getTargetId())
                .orderByDesc(GroupMessages::getMessageTime)
                .last("LIMIT " + PAGE_SIZE);
        return this.list(wrapper);
    }

    @Override
    public List<GroupMessages> getMessagesByMessageId(QueryMessagesByMessageIdRequest request) {
        LambdaQueryWrapper<GroupMessages> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(GroupMessages::getBotId, request.getBotId())
                .eq(GroupMessages::getGroupId, request.getTargetId())
                .lt(GroupMessages::getMessageTime, 
                    this.getOne(new LambdaQueryWrapper<GroupMessages>()
                            .eq(GroupMessages::getMessageId, request.getMessageId()))
                            .getMessageTime())
                .orderByDesc(GroupMessages::getMessageTime)
                .last("LIMIT " + PAGE_SIZE);
        return this.list(wrapper);
    }

    @Override
    public List<GroupMessages> getMessagesBySender(QueryMessagesBySenderRequest request) {
        LambdaQueryWrapper<GroupMessages> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(GroupMessages::getBotId, request.getBotId())
                .eq(GroupMessages::getSenderId, request.getSenderId())
                .orderByDesc(GroupMessages::getMessageTime)
                .last("LIMIT " + PAGE_SIZE);
        return this.list(wrapper);
    }

    @Override
    public List<GroupMessages> searchMessages(SearchMessagesRequest request) {
        LambdaQueryWrapper<GroupMessages> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(GroupMessages::getBotId, request.getBotId())
                .eq(GroupMessages::getGroupId, request.getTargetId())
                .like(GroupMessages::getParseMessage, request.getKeyword())
                .orderByDesc(GroupMessages::getMessageTime);
        return this.list(wrapper);
    }

    @Override
    public GroupMessages getMessageById(Integer id) {
        return this.getById(id);
    }

    @Override
    public GroupMessages addGroupMessage(GroupMessageEvent event) {
        try {
            GroupMessages groupMessages = new GroupMessages();
            groupMessages.setBotId(event.getSelfId());
            groupMessages.setGroupId(event.getGroupId());
            groupMessages.setMessageId(event.getMessageId());
            groupMessages.setSenderId(event.getUserId());
            groupMessages.setMessageSegments(objectMapper.writeValueAsString(event.getMessage()));
            groupMessages.setMessageTime(event.getTime() * 1000L);
            groupMessages.setParseMessage(event.getParsedMessage());
            this.save(groupMessages);
            return groupMessages;
        } catch (Exception e) {
            throw new RuntimeException("Failed to add group message", e);
        }
    }

    @Override
    public Boolean deleteMessage(Integer id) {
        GroupMessages message = this.getById(id);
        if (message == null) {
            return false;
        }
        message.setIsDelete(1);
        return this.updateById(message);
    }

    @Override
    public Boolean updateMessage(UpdateMessageRequest request) {
        GroupMessages message = this.getById(request.getId());
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




