package org.mango.mangobot.model.onebot;

import lombok.Data;
import org.mango.mangobot.model.onebot.segment.MessageSegment;

import java.util.List;

@Data
public class SendMessage {
    List<MessageSegment> message;
}
