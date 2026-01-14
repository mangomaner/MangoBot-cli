package io.github.mangomaner.mangobot.model.onebot;

import io.github.mangomaner.mangobot.model.onebot.segment.MessageSegment;
import lombok.Data;

import java.util.List;

@Data
public class SendMessage {
    List<MessageSegment> message;
}
