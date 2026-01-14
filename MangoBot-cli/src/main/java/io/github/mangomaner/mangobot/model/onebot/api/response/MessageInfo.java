package io.github.mangomaner.mangobot.model.onebot.api.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class MessageInfo {
    private int time;
    
    @JsonProperty("message_type")
    private String messageType;
    
    @JsonProperty("message_id")
    private int messageId;
    
    @JsonProperty("real_id")
    private int realId;
    
    private Object sender;
    
    private Object message;
}
