package org.mango.mangobot.model.onebot.api.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

@Data
public class GroupHonorInfo {
    @JsonProperty("group_id")
    private long groupId;
    
    @JsonProperty("current_talkative")
    private HonorNode currentTalkative;
    
    @JsonProperty("talkative_list")
    private List<HonorNode> talkativeList;
    
    @JsonProperty("performer_list")
    private List<HonorNode> performerList;
    
    @JsonProperty("legend_list")
    private List<HonorNode> legendList;
    
    @JsonProperty("strong_newbie_list")
    private List<HonorNode> strongNewbieList;
    
    @JsonProperty("emotion_list")
    private List<HonorNode> emotionList;
    
    @Data
    public static class HonorNode {
        @JsonProperty("user_id")
        private long userId;
        private String nickname;
        private String avatar;
        @JsonProperty("day_count")
        private Integer dayCount;
        private String description;
    }
}
