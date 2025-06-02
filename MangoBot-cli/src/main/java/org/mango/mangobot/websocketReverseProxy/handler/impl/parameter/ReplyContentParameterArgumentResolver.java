package org.mango.mangobot.websocketReverseProxy.handler.impl.parameter;


import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.mango.mangobot.QQ.parameter.ReplyContent;
import org.mango.mangobot.messageHandler.messageStore.collection.QQMessageCollection;
import org.mango.mangobot.model.QQ.QQMessage;
import org.mango.mangobot.model.QQ.ReceiveMessageSegment;
import org.mango.mangobot.utils.MethodParameter;
import org.mango.mangobot.websocketReverseProxy.handler.ParameterArgumentResolver;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ReplyContentParameterArgumentResolver implements ParameterArgumentResolver {
//    @Resource
//    private ElasticsearchClient client;
    @Resource
    private ObjectMapper objectMapper;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasAnnotation(ReplyContent.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, QQMessage message) {
        List<ReceiveMessageSegment> segments = message.getMessage();
        Integer messageId = null;
        for (ReceiveMessageSegment segment : segments) {
            if ("reply".equalsIgnoreCase(segment.getType())) {
                messageId = Integer.parseInt(segment.getData().getId());
                break;
            }
        }

        // 如果找到了有效的 messageId，则从数据库中获取消息内容
        if (messageId != null) {
            QQMessageCollection qqMessageCollection = findMessageById(messageId, message.getGroup_id());
            return parseMessageContent(qqMessageCollection);
        }

        return "";
    }

    private QQMessageCollection findMessageById(Integer messageId, String groupId) {
//        GetRequest request = GetRequest.of(b -> b
//                .index("chat_group_" + groupId)
//                .id(messageId.toString())
//        );
//
//        GetResponse<Object> response = null;
//        try {
//            response = client.get(request, Object.class);
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
//
//        if (!response.found()) {
//            return null;
//        }
//
//        Map<String, Object> source = (Map<String, Object>) response.source();
//        return objectMapper.convertValue(source, QQMessageCollection.class);
        return null;
    }

    private String parseMessageContent(QQMessageCollection qqMessageCollection) {
        List<ReceiveMessageSegment> messageList = qqMessageCollection.getMessage();

        StringBuilder contentBuilder = new StringBuilder();
        for(ReceiveMessageSegment message : messageList){
            switch (message.getType()){
                case "text":
                    contentBuilder.append(message.getData().getText());
                    break;
                case "image":
                    contentBuilder.append(message.getData().getUrl());
                    break;
                case "file":
                    contentBuilder.append(message.getData().getUrl());
                    break;
                case "at":
                    contentBuilder.append(message.getData().getQq());
            }
        }
        return contentBuilder.toString();
    }
}
