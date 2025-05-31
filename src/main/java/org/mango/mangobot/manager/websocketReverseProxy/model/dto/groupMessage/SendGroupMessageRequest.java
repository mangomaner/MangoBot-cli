package org.mango.mangobot.manager.websocketReverseProxy.model.dto.groupMessage;

import lombok.Data;

import java.util.List;

/**
 * 消息格式（该类实现params内层）：
 * {
 *     "action": "send_group_msg",
 *     "params": {
 *       "group_id": 545402644,
 *       "message": [
 *         {
 *           "type": "text",
 *           "data": {
 *             "text": "HelloKitty"
 *           }
 *         }
 *       ]
 *     },
 *     "echo": "唯一标识，如 uuid"
 * }
 */

/**
 * 发送群消息的请求体封装类
 */
@Data
public class SendGroupMessageRequest {
    /**
     * 群号
     */
    private String group_id;

    /**
     * 消息内容列表，支持多个消息段组合
     */
    private List<MessageSegment> message;
}