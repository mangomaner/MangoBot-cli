package org.mango.mangobot.model.onebot.event.message;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.mango.mangobot.model.onebot.event.MessageEvent;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@JsonTypeName("private")
public class PrivateMessageEvent extends MessageEvent {
    // Private message specific fields if any
}
