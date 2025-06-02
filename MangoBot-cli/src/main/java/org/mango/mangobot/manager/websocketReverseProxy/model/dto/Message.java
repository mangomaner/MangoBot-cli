package org.mango.mangobot.manager.websocketReverseProxy.model.dto;

import lombok.Data;

/**
 * webSocket通信类，需将接口的json内容放入param中
 */
@Data
public class Message<T> {
    private String action;
    private String echo;
    private T params;
}
