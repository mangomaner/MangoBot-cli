package io.github.mangomaner.mangobot.api;

import io.github.mangomaner.mangobot.model.domain.GroupMessages;
import io.github.mangomaner.mangobot.model.dto.message.QueryLatestMessagesRequest;
import io.github.mangomaner.mangobot.model.dto.message.QueryMessagesByMessageIdRequest;
import io.github.mangomaner.mangobot.model.dto.message.QueryMessagesBySenderRequest;
import io.github.mangomaner.mangobot.model.dto.message.SearchMessagesRequest;
import io.github.mangomaner.mangobot.model.dto.message.UpdateMessageRequest;
import io.github.mangomaner.mangobot.model.vo.GroupMessageVO;
import io.github.mangomaner.mangobot.service.GroupMessagesService;

import java.util.List;

/**
 * 群聊消息 API (静态工具类)
 * 提供对群聊消息的查询和更新能力，不允许新增和删除。
 */
public class MangoGroupMessageApi {

    private static GroupMessagesService service;

    private MangoGroupMessageApi() {}

    static void setService(GroupMessagesService service) {
        MangoGroupMessageApi.service = service;
    }

    private static void checkService() {
        if (service == null) {
            throw new IllegalStateException("MangoGroupMessageApi has not been initialized yet.");
        }
    }

    public static List<GroupMessages> getLatestMessages(QueryLatestMessagesRequest request) {
        checkService();
        return service.getLatestMessages(request);
    }

    public static List<GroupMessages> getMessagesByMessageId(QueryMessagesByMessageIdRequest request) {
        checkService();
        return service.getMessagesByMessageId(request);
    }

    public static List<GroupMessages> getMessagesBySender(QueryMessagesBySenderRequest request) {
        checkService();
        return service.getMessagesBySender(request);
    }

    public static List<GroupMessages> searchMessages(SearchMessagesRequest request) {
        checkService();
        return service.searchMessages(request);
    }

    public static GroupMessages getMessageById(Integer id) {
        checkService();
        return service.getMessageById(id);
    }

    public static Boolean updateMessage(UpdateMessageRequest request) {
        checkService();
        return service.updateMessage(request);
    }

    public static List<GroupMessageVO> convertToVOList(List<GroupMessages> messages) {
        checkService();
        return service.convertToVOList(messages);
    }

    public static GroupMessageVO convertToVO(GroupMessages message) {
        checkService();
        return service.convertToVO(message);
    }
}
