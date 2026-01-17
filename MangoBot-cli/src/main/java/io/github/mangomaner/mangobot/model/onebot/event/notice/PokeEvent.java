package io.github.mangomaner.mangobot.model.onebot.event.notice;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import io.github.mangomaner.mangobot.model.onebot.event.NoticeEvent;

/**
 * 戳一戳
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@JsonTypeName("notify")
@JsonDeserialize(using = com.fasterxml.jackson.databind.JsonDeserializer.None.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public class PokeEvent extends NoticeEvent {
    @JsonProperty("sub_type")
    private String subType; // poke
    
    @JsonProperty("target_id")
    private long targetId;
    
    @JsonProperty("user_id")
    private long userId;
    
    @JsonProperty("group_id")
    private Long groupId; // Nullable for private poke
}
