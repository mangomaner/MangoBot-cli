package org.mango.mangobot.manager.websocketReverseProxy.dispatcher.impl;

public enum MessageTypeEnum {
    TEXT("text"),
    IMAGE("image"),
    AT("at"),
    REPLY("reply"),
    AUDIO("audio"),
    POKE("poke"),
    DEFAULT("default");

    private String value;

    MessageTypeEnum(String value){
        this.value = value;
    }

    public String getValue(){
        return this.value;
    }
}
