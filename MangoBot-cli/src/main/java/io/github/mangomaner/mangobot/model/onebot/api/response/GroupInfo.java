package io.github.mangomaner.mangobot.model.onebot.api.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class GroupInfo {
    @JsonProperty("group_id")
    private long groupId;
    
    @JsonProperty("group_name")
    private String groupName;
    
    @JsonProperty("member_count")
    private int memberCount;
    
    @JsonProperty("max_member_count")
    private int maxMemberCount;
}
