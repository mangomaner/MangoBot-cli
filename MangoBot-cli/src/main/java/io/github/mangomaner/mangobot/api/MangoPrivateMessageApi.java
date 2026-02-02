package io.github.mangomaner.mangobot.api;

import io.github.mangomaner.mangobot.model.domain.PrivateMessages;
import io.github.mangomaner.mangobot.model.dto.message.QueryLatestMessagesRequest;
import io.github.mangomaner.mangobot.model.dto.message.QueryMessagesByMessageIdRequest;
import io.github.mangomaner.mangobot.model.dto.message.QueryMessagesBySenderRequest;
import io.github.mangomaner.mangobot.model.dto.message.SearchMessagesRequest;
import io.github.mangomaner.mangobot.model.dto.message.UpdateMessageRequest;
import io.github.mangomaner.mangobot.model.vo.PrivateMessageVO;
import io.github.mangomaner.mangobot.service.PrivateMessagesService;

import java.util.List;

/**
 * 私聊消息 API (静态工具类)
 * 提供对私聊消息的查询和更新能力，不允许新增和删除。
 */
public class MangoPrivateMessageApi {

    private static PrivateMessagesService service;

    private MangoPrivateMessageApi() {}

    static void setService(PrivateMessagesService service) {
        MangoPrivateMessageApi.service = service;
    }

    private static void checkService() {
        if (service == null) {
            throw new IllegalStateException("MangoPrivateMessageApi has not been initialized yet.");
        }
    }

    public static List<PrivateMessages> getLatestMessages(QueryLatestMessagesRequest request) {
        checkService();
        return service.getLatestMessages(request);
    }

    public static List<PrivateMessages> getMessagesByMessageId(QueryMessagesByMessageIdRequest request) {
        checkService();
        return service.getMessagesByMessageId(request);
    }

    public static List<PrivateMessages> getMessagesBySender(QueryMessagesBySenderRequest request) {
        checkService();
        return service.getMessagesBySender(request);
    }

    public static List<PrivateMessages> searchMessages(SearchMessagesRequest request) {
        checkService();
        return service.searchMessages(request);
    }

    public static PrivateMessages getMessageById(Integer id) {
        checkService();
        return service.getMessageById(id);
    }

    public static Boolean updateMessage(UpdateMessageRequest request) {
        checkService();
        return service.updateMessage(request);
    }

    public static List<PrivateMessageVO> convertToVOList(List<PrivateMessages> messages) {
        checkService();
        return service.convertToVOList(messages);
    }

    public static PrivateMessageVO convertToVO(PrivateMessages message) {
        checkService();
        return service.convertToVO(message);
    }
}
