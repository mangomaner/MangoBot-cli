package io.github.mangomaner.mangobot.service;

import io.github.mangomaner.mangobot.model.domain.GroupMessages;
import io.github.mangomaner.mangobot.model.dto.message.QueryLatestMessagesRequest;
import io.github.mangomaner.mangobot.model.dto.message.QueryMessagesByMessageIdRequest;
import io.github.mangomaner.mangobot.model.dto.message.QueryMessagesBySenderRequest;
import io.github.mangomaner.mangobot.model.dto.message.SearchMessagesRequest;
import io.github.mangomaner.mangobot.model.dto.message.UpdateMessageRequest;
import io.github.mangomaner.mangobot.model.onebot.event.message.GroupMessageEvent;
import io.github.mangomaner.mangobot.model.onebot.segment.MessageSegment;
import io.github.mangomaner.mangobot.model.vo.GroupMessageVO;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
* @author mangoman
* @description 针对表【group_messages】的数据库操作Service
* @createDate 2026-01-17 18:03:14
*/
public interface GroupMessagesService extends IService<GroupMessages> {

    List<GroupMessages> getLatestMessages(QueryLatestMessagesRequest request);

    List<GroupMessages> getMessagesByMessageId(QueryMessagesByMessageIdRequest request);

    List<GroupMessages> getMessagesBySender(QueryMessagesBySenderRequest request);

    List<GroupMessages> searchMessages(SearchMessagesRequest request);

    GroupMessages getMessageById(Integer id);

    GroupMessages addGroupMessage(GroupMessageEvent event);

    GroupMessages addGroupMessage(List<MessageSegment> segments, Long botId, Long groupId, Integer messageId);

    Boolean deleteMessage(Integer id);

    Boolean updateMessage(UpdateMessageRequest request);

    List<GroupMessageVO> convertToVOList(List<GroupMessages> messages);

    GroupMessageVO convertToVO(GroupMessages message);
}
