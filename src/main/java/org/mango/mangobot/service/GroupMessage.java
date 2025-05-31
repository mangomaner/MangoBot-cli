package org.mango.mangobot.service;


import java.util.List;

public interface GroupMessage {
    /**
     * 发送纯文本消息
     */
    public void sendTextMessage(String groupId, String text);

    /**
     * 发送带 @ 的消息
     */
    public void sendAtMessage(String groupId, String qq, String text);

    /**
     * 发送图片消息
     */
    public void sendImageMessage(String groupId, String fileUrlOrPath);

    /**
     * 发送语音消息
     */
    public void sendRecordMessage(String groupId, String fileUrlOrPath);

    /**
     * 发送回复消息
     */
    public void sendReplyMessage(String groupId, String messageId, String message);

    /**
     * 发送混合消息（可自定义多个 MessageSegment）
     */
    public void sendCustomMessage(String groupId, String text, String qq, String imageUrl);

}
