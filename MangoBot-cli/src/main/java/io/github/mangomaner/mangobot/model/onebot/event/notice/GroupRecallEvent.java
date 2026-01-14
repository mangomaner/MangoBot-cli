package io.github.mangomaner.mangobot.model.onebot.event.notice;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import io.github.mangomaner.mangobot.model.onebot.event.NoticeEvent;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@JsonTypeName("group_recall")
@JsonDeserialize(using = com.fasterxml.jackson.databind.JsonDeserializer.None.class)
public class GroupRecallEvent extends NoticeEvent {
    @JsonProperty("group_id")
    private long groupId;
    
    @JsonProperty("user_id")
    private long userId;
    
    @JsonProperty("operator_id")
    private long operatorId;
    
    @JsonProperty("message_id")
    private int messageId;
}
