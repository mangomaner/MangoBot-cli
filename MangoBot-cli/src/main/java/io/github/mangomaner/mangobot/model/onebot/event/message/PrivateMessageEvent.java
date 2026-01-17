package io.github.mangomaner.mangobot.model.onebot.event.message;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import io.github.mangomaner.mangobot.model.onebot.event.MessageEvent;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@JsonTypeName("private")
public class PrivateMessageEvent extends MessageEvent {
    // Private message specific fields if any
    private String parsedMessage;
}
