package io.github.mangomaner.mangobot.adapter.onebot.model.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public abstract class OneBotBaseEvent implements OneBotEvent {
    private long time;
    
    @JsonProperty("self_id")
    private long selfId;
    
    @JsonProperty("post_type")
    private String postType;
}
