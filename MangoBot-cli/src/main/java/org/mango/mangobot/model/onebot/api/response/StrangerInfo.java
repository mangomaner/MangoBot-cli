package org.mango.mangobot.model.onebot.api.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class StrangerInfo {
    @JsonProperty("user_id")
    private long userId;
    
    private String nickname;
    
    private String sex;
    
    private int age;
}
