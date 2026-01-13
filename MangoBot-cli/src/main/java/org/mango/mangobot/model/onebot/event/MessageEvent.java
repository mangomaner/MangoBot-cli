package org.mango.mangobot.model.onebot.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.mango.mangobot.model.onebot.segment.MessageSegment;
import org.mango.mangobot.model.onebot.segment.TextSegment;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@JsonDeserialize(using = JsonDeserializer.None.class)
public abstract class MessageEvent extends BaseEvent {
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

    @JsonProperty("raw_message")
    private String rawMessage;

    @JsonProperty("raw_pb")
    private String rawPb;

    private int font;

    private List<MessageSegment> message;
    
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

    public String getPlainText() {
        if (message == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (MessageSegment segment : message) {
            if ("text".equals(segment.getType()) && segment instanceof TextSegment textSegment) {
                sb.append((textSegment).getText());
            }
        }
        return sb.toString();
    }
}
