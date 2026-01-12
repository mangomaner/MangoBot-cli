package org.mango.mangobot.model.onebot.event.notice;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.mango.mangobot.model.onebot.event.NoticeEvent;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@JsonTypeName("group_increase")
@JsonDeserialize(using = com.fasterxml.jackson.databind.JsonDeserializer.None.class)
public class GroupIncreaseEvent extends NoticeEvent {
    @JsonProperty("sub_type")
    private String subType; // approve, invite
    
    @JsonProperty("group_id")
    private long groupId;
    
    @JsonProperty("operator_id")
    private long operatorId;
    
    @JsonProperty("user_id")
    private long userId;
}
