package org.mango.mangobot.messageHandler.messageStore;

import org.mango.mangobot.model.QQ.QQMessage;
import org.springframework.stereotype.Service;

@Service
public interface ChatMessageStoreService {

    /**
     * 将消息存储到数据库
     *
     * @param qqMessage 消息对象
     * @param groupId   群组 ID
     */
    void saveMessageToDatabase(QQMessage qqMessage, String groupId);

    /**
     * 更新某条消息的撤回状态（设置 isDelete: true）
     *
     * @param groupId   群ID
     * @param messageId 消息ID
     */
    void updateRecallStatus(Long groupId, Long messageId);

    /**
     * 通过 echo 找到未发送消息并更新 message_id 后写入数据库
     *
     * @param echo      echo 标识
     * @param messageId server 返回的 message_id
     */
    void updateSentMessageEchoId(String echo, Long messageId);
}