package io.github.mangomaner.mangobot.model.onebot.event.notice;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import io.github.mangomaner.mangobot.model.onebot.event.NoticeEvent;

/**
 * 群精华消息
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@JsonTypeName("essence")
@JsonDeserialize(using = com.fasterxml.jackson.databind.JsonDeserializer.None.class)
public class EssenceEvent extends NoticeEvent {
    @JsonProperty("sub_type")
    private String subType; // add, delete
    
    @JsonProperty("group_id")
    private long groupId;
    
    @JsonProperty("operator_id")
    private long operatorId;
    
    @JsonProperty("sender_id")
    private long senderId;
    
    @JsonProperty("message_id")
    private int messageId;
}
