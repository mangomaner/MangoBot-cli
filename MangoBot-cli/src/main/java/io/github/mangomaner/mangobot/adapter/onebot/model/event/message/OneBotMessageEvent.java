package io.github.mangomaner.mangobot.adapter.onebot.model.event.message;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.github.mangomaner.mangobot.adapter.onebot.model.event.OneBotBaseEvent;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import io.github.mangomaner.mangobot.adapter.onebot.model.segment.OneBotMessageSegment;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public abstract class OneBotMessageEvent extends OneBotBaseEvent {
    @JsonProperty("message_type")
    private String messageType;

    @JsonProperty("sub_type")
    private String subType;

    @JsonProperty("message_id")
    private int messageId;

    @JsonProperty("user_id")
    private long userId;
    
    @JsonProperty("message_seq")
    private int messageSeq;

    @JsonProperty("real_id")    
    private int realId;     // NapCat

    @JsonProperty("real_seq")
    private String realSeq; // NapCat

    @JsonProperty("raw_message")
    private String rawMessage;

    @JsonProperty("raw_pb")
    private String rawPb;

    private int font;

    private List<OneBotMessageSegment> message;
    
    @JsonProperty("message_format")
    private String messageFormat;
    
    private Sender sender;
    
    @Data
    public static class Sender {
        @JsonProperty("user_id")
        private long userId;
        private String nickname;
        private String card;
        private String role;
        private String title;
    }

}
