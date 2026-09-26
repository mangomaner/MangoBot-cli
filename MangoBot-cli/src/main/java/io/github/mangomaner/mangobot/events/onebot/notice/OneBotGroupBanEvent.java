package io.github.mangomaner.mangobot.events.onebot.notice;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.github.mangomaner.mangobot.adapter.onebot.model.event.notice.OneBotNoticeEvent;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 群禁言
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@JsonTypeName("group_ban")
public class OneBotGroupBanEvent extends OneBotNoticeEvent {
    @JsonProperty("sub_type")
    private String subType; // ban, lift_ban
    
    @JsonProperty("group_id")
    private long groupId;
    
    @JsonProperty("operator_id")
    private long operatorId;
    
    @JsonProperty("user_id")
    private long userId; // 0 for all
    
    private long duration; // seconds
}
