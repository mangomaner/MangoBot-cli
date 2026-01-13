package org.mango.mangobot.model.onebot.api.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class LoginInfo {
    @JsonProperty("user_id")
    private long userId;
    
    private String nickname;
}
