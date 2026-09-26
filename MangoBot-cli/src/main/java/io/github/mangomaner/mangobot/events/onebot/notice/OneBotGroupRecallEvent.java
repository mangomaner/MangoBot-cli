package io.github.mangomaner.mangobot.events.onebot.notice;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.github.mangomaner.mangobot.adapter.onebot.model.event.notice.OneBotNoticeEvent;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 群消息撤回
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@JsonTypeName("group_recall")
public class OneBotGroupRecallEvent extends OneBotNoticeEvent {
    @JsonProperty("group_id")
    private long groupId;
    
    @JsonProperty("user_id")
    private long userId;
    
    @JsonProperty("operator_id")
    private long operatorId;
    
    @JsonProperty("message_id")
    private int messageId;
}
