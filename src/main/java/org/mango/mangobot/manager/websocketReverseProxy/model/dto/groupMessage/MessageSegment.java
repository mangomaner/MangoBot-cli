package org.mango.mangobot.manager.websocketReverseProxy.model.dto.groupMessage;

import lombok.Data;

/**
 * 消息片段基类
 */
@Data
public abstract class MessageSegment {
    /**
     * 消息类型：text, reply, at, image, record 等
     */
    private String type;
}