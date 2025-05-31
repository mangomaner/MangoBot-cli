package org.mango.mangobot.messageHandler.messageStore.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.UpdateRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.mango.mangobot.messageHandler.messageStore.ChatMessageStoreService;
import org.mango.mangobot.messageHandler.messageStore.collection.QQMessageCollection;
import org.mango.mangobot.model.QQ.QQMessage;
import org.springframework.beans.BeanUtils;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 这里留了存入ES的例子，如果您希望配置数据库，请自行配置或编写代码
 */
@Service
@Slf4j
@Primary
public class EsChatMessageStoreService implements ChatMessageStoreService {

//    @Resource
//    private ElasticsearchClient elasticsearchClient;
    @Resource
    private ObjectMapper objectMapper;
    @Resource
    private Map<String, QQMessage> echoMap;

    /**
     * 获取群消息对应的索引名称
     */
    private String getIndexName(String groupId) {
        return "chat_group_" + groupId;
    }

    @Override
    public void saveMessageToDatabase(QQMessage qqMessage, String groupId) {
//        if (qqMessage.getMessage_id() == null || qqMessage.getMessage_id() <= 0) {
//            System.out.println("message_id 为空或无效，跳过该条消息");
//            return;
//        }
//        QQMessageCollection collection = new QQMessageCollection();
//        BeanUtils.copyProperties(qqMessage, collection);
//        try {
//            String indexName = getIndexName(groupId);
//            String json = objectMapper.writeValueAsString(collection);
//
//            IndexRequest<ByteArrayInputStream> request = IndexRequest.of(b -> b
//                    .index(indexName)
//                    .id(qqMessage.getMessage_id().toString())
//                    .withJson(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)))
//            );
//
//            elasticsearchClient.index(request);
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
    }

    @Override
    public void updateRecallStatus(Long groupId, Long messageId) {
//        try {
//            String indexName = getIndexName(groupId.toString());
//
//            UpdateRequest<Map<String, Object>, Map<String, Object>> request = UpdateRequest.of(b -> b
//                    .index(indexName)
//                    .id(messageId.toString())
//                    .doc(Map.of("isDelete", true))
//            );
//
//            elasticsearchClient.update(request, Map.class);
//        } catch (IOException e) {
//            Thread.currentThread().interrupt();
//            throw new RuntimeException("Failed to update recall status in ES", e);
//        }
    }

    @Override
    public void updateSentMessageEchoId(String echo, Long messageId) {
//        QQMessage qqMessage = echoMap.get(echo);
//        if (qqMessage == null) {
//            System.out.println("echo 为空，无法更新发送的消息");
//            return;
//        }
//
//        qqMessage.setMessage_id(messageId);
//        saveMessageToDatabase(qqMessage, qqMessage.getGroup_id());
    }
}