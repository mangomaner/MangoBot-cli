package io.github.mangomaner.mangobot.model.onebot.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Data;

@Data
@JsonDeserialize(using = io.github.mangomaner.mangobot.model.onebot.event.EventDeserializer.class)
public abstract class BaseEvent implements Event {
    private long time;
    
    @JsonProperty("self_id")
    private long selfId;
    
    @JsonProperty("post_type")
    private String postType;
}
