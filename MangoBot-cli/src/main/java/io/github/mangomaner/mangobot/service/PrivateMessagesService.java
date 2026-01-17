package io.github.mangomaner.mangobot.service;

import io.github.mangomaner.mangobot.model.domain.PrivateMessages;
import io.github.mangomaner.mangobot.model.dto.message.QueryLatestMessagesRequest;
import io.github.mangomaner.mangobot.model.dto.message.QueryMessagesByMessageIdRequest;
import io.github.mangomaner.mangobot.model.dto.message.QueryMessagesBySenderRequest;
import io.github.mangomaner.mangobot.model.dto.message.SearchMessagesRequest;
import io.github.mangomaner.mangobot.model.dto.message.UpdateMessageRequest;
import io.github.mangomaner.mangobot.model.onebot.event.message.PrivateMessageEvent;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
* @author mangoman
* @description 针对表【private_messages】的数据库操作Service
* @createDate 2026-01-17 18:03:57
*/
public interface PrivateMessagesService extends IService<PrivateMessages> {

    List<PrivateMessages> getLatestMessages(QueryLatestMessagesRequest request);

    List<PrivateMessages> getMessagesByMessageId(QueryMessagesByMessageIdRequest request);

    List<PrivateMessages> getMessagesBySender(QueryMessagesBySenderRequest request);

    List<PrivateMessages> searchMessages(SearchMessagesRequest request);

    PrivateMessages getMessageById(Integer id);

    PrivateMessages addPrivateMessage(PrivateMessageEvent event);

    Boolean deleteMessage(Integer id);

    Boolean updateMessage(UpdateMessageRequest request);
}
