package io.github.mangomaner.mangobot.events.onebot.notice;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.github.mangomaner.mangobot.adapter.onebot.model.event.notice.OneBotNoticeEvent;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 戳一戳
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@JsonTypeName("notify")
@JsonIgnoreProperties(ignoreUnknown = true)
public class PokeEvent extends OneBotNoticeEvent {
    @JsonProperty("sub_type")
    private String subType; // poke
    
    @JsonProperty("target_id")
    private long targetId;
    
    @JsonProperty("user_id")
    private long userId;
    
    @JsonProperty("group_id")
    private Long groupId; // Nullable for private poke
}
