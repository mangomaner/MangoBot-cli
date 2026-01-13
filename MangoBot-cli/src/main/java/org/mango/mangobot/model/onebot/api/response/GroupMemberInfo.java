package org.mango.mangobot.model.onebot.api.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class GroupMemberInfo {
    @JsonProperty("group_id")
    private long groupId;
    
    @JsonProperty("user_id")
    private long userId;
    
    private String nickname;
    
    private String card;
    
    private String sex;
    
    private int age;
    
    private String area;
    
    @JsonProperty("join_time")
    private int joinTime;
    
    @JsonProperty("last_sent_time")
    private int lastSentTime;
    
    private String level;
    
    private String role;
    
    private boolean unfriendly;
    
    private String title;
    
    @JsonProperty("title_expire_time")
    private int titleExpireTime;
    
    @JsonProperty("card_changeable")
    private boolean cardChangeable;
}
