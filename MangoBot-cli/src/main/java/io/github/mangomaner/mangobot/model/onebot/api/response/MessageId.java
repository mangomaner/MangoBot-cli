package io.github.mangomaner.mangobot.model.onebot.api.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class MessageId {
    @JsonProperty("message_id")
    private int messageId;
}
