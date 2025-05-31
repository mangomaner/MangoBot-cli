package org.mango.mangobot.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.mango.mangobot.common.ErrorCode;
import org.mango.mangobot.exception.BusinessException;
import org.mango.mangobot.manager.websocketReverseProxy.model.dto.Message;
import org.mango.mangobot.manager.websocketReverseProxy.model.dto.groupMessage.*;
import org.mango.mangobot.model.QQ.MessageData;
import org.mango.mangobot.model.QQ.QQMessage;
import org.mango.mangobot.model.QQ.ReceiveMessageSegment;
import org.mango.mangobot.service.GroupMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 消息发送类，不仅要构建发送的消息，还要构建接收的消息，原因如下：
 *      发送的消息并不会收到消息通知，只会收到一个回应的echo消息，echo消息则为自己发送时定义的唯一标识符
 *      所以，发送的消息需要先保存到数据库中，且id为echo，这样在收到消息回传就可以根据 echo 值找到对应的消息，将消息id变为message_id
 */

@Service
@Slf4j
public class GroupMessageService implements GroupMessage {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Resource
    private Map<String, WebSocketSession> sessionMap;

    @Resource
    private Map<String, QQMessage> echoMap;

    @Value("${QQ.botQQ}")
    private String selfId;

    // ================== 工具方法：构建消息段 ==================
    private static class MessageSegmentFactory {
        static List<MessageSegment> buildSendSegments(String text, String qq, String imageUrl) {
            List<MessageSegment> segments = new ArrayList<>();
            if (qq != null && !qq.isEmpty()) {
                AtMessageData at = new AtMessageData();
                at.getData().setQq(qq);
                segments.add(at);
            }
            if (text != null && !text.isEmpty()) {
                TextMessageData textMsg = new TextMessageData();
                textMsg.getData().setText(text);
                segments.add(textMsg);
            }
            if (imageUrl != null && !imageUrl.isEmpty()) {
                ImageMessageData image = new ImageMessageData();
                image.getData().setFile(imageUrl);
                segments.add(image);
            }
            return segments;
        }

        static List<ReceiveMessageSegment> buildReceiveSegments(String text, String qq, String imageUrl) {
            List<ReceiveMessageSegment> segments = new ArrayList<>();
            if (qq != null && !qq.isEmpty()) {
                ReceiveMessageSegment seg = new ReceiveMessageSegment();
                seg.setType("at");
                MessageData data = new MessageData();
                data.setQq(qq);
                seg.setData(data);
                segments.add(seg);
            }
            if (text != null && !text.isEmpty()) {
                ReceiveMessageSegment seg = new ReceiveMessageSegment();
                seg.setType("text");
                MessageData data = new MessageData();
                data.setText(text);
                seg.setData(data);
                segments.add(seg);
            }
            if (imageUrl != null && !imageUrl.isEmpty()) {
                ReceiveMessageSegment seg = new ReceiveMessageSegment();
                seg.setType("image");
                MessageData data = new MessageData();
                data.setUrl(imageUrl);
                seg.setData(data);
                segments.add(seg);
            }
            return segments;
        }
    }

    // ================== 通用发送方法 ==================

    private void sendGenericMessage(String groupId, List<MessageSegment> sendSegments,
                                    List<ReceiveMessageSegment> receiveSegments) {
        try {
            QQMessage qqMessage = new QQMessage();
            qqMessage.setSelf_id(selfId);
            qqMessage.setGroup_id(groupId);
            qqMessage.setMessage(receiveSegments);

            SendGroupMessageRequest request = new SendGroupMessageRequest();
            request.setGroup_id(groupId);
            request.setMessage(sendSegments);

            sendMessage("send_group_msg", request, qqMessage);
        } catch (Exception e) {
            log.error("通用消息发送失败", e);
        }
    }

    // ================== 接口实现方法 ==================

    @Override
    public void sendTextMessage(String groupId, String text) {
        List<MessageSegment> sendSegments = MessageSegmentFactory.buildSendSegments(text, null, null);
        List<ReceiveMessageSegment> receiveSegments = MessageSegmentFactory.buildReceiveSegments(text, null, null);
        sendGenericMessage(groupId, sendSegments, receiveSegments);
    }

    @Override
    public void sendAtMessage(String groupId, String qq, String text) {
        if (text != null && !text.isEmpty()) {
            text = " " + text; // 添加空格以避免@后面直接接文字显示异常
        }
        List<MessageSegment> sendSegments = MessageSegmentFactory.buildSendSegments(text, qq, null);
        List<ReceiveMessageSegment> receiveSegments = MessageSegmentFactory.buildReceiveSegments(text, qq, null);
        sendGenericMessage(groupId, sendSegments, receiveSegments);
    }

    @Override
    public void sendImageMessage(String groupId, String fileUrlOrPath) {
        List<MessageSegment> sendSegments = MessageSegmentFactory.buildSendSegments(null, null, fileUrlOrPath);
        List<ReceiveMessageSegment> receiveSegments = MessageSegmentFactory.buildReceiveSegments(null, null, fileUrlOrPath);
        sendGenericMessage(groupId, sendSegments, receiveSegments);
    }

    @Override
    public void sendRecordMessage(String groupId, String fileUrlOrPath) {
        ReceiveMessageSegment audioSeg = new ReceiveMessageSegment();
        audioSeg.setType("record");
        MessageData data = new MessageData();
        data.setUrl(fileUrlOrPath);
        audioSeg.setData(data);

        QQMessage qqMessage = new QQMessage();
        qqMessage.setSelf_id(selfId);
        qqMessage.setGroup_id(groupId);
        qqMessage.setMessage(List.of(audioSeg));

        SendGroupMessageRequest request = new SendGroupMessageRequest();
        request.setGroup_id(groupId);

        RecordMessageData record = new RecordMessageData();
        record.getData().setFile(fileUrlOrPath);
        request.setMessage(List.of(record));

        sendMessage("send_group_msg", request, qqMessage);
    }

    @Override
    public void sendReplyMessage(String groupId, String messageId, String message) {
        List<MessageSegment> sendSegments = new ArrayList<>();

        ReplyMessageData reply = new ReplyMessageData();
        reply.getData().setId(messageId);
        sendSegments.add(reply);

        TextMessageData text = new TextMessageData();
        text.getData().setText(message);
        sendSegments.add(text);

        List<ReceiveMessageSegment> receiveSegments = new ArrayList<>();

        ReceiveMessageSegment replySeg = new ReceiveMessageSegment();
        replySeg.setType("reply");
        MessageData replyData = new MessageData();
        replyData.setId(messageId);
        replySeg.setData(replyData);
        receiveSegments.add(replySeg);

        ReceiveMessageSegment textSeg = new ReceiveMessageSegment();
        textSeg.setType("text");
        MessageData textData = new MessageData();
        textData.setText(message);
        textSeg.setData(textData);
        receiveSegments.add(textSeg);

        sendGenericMessage(groupId, sendSegments, receiveSegments);
    }

    @Override
    public void sendCustomMessage(String groupId, String text, String qq, String imageUrl) {
        if (text == null && qq == null && imageUrl == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "当前发送的是空消息");
        }

        if (text != null && !text.isEmpty()) {
            text = " " + text;
        }

        List<MessageSegment> sendSegments = MessageSegmentFactory.buildSendSegments(text, qq, imageUrl);
        List<ReceiveMessageSegment> receiveSegments = MessageSegmentFactory.buildReceiveSegments(text, qq, imageUrl);

        sendGenericMessage(groupId, sendSegments, receiveSegments);
    }

    // ================== 内部方法 ==================

    private <T> void sendMessage(String action, T params, QQMessage qqMessage) {
        try {
            WebSocketSession session = sessionMap.get(selfId);
            if (session == null || !session.isOpen()) {
                log.warn("QQ号 {} 当前没有活跃连接", selfId);
                return;
            }

            String echo = java.util.UUID.randomUUID().toString();
            Message<T> messageWrapper = new Message<>();
            messageWrapper.setAction(action);
            messageWrapper.setParams(params);
            messageWrapper.setEcho(echo);

            echoMap.put(echo, qqMessage);   // 暂存echo，等待messageId

            String json = objectMapper.writeValueAsString(messageWrapper);
            session.sendMessage(new TextMessage(json));
            log.info("消息已发送: {}", json);
        } catch (Exception e) {
            log.error("发送消息时出错: ", e);
        }
    }
}