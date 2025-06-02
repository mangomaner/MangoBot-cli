package org.mango.mangobot.model.QQ;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * 接收到的完整QQ消息，供开发使用
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class QQMessage {
    private String self_id;
    private String user_id;
    private Long time;
    private Long message_id;
    private String message_type;
    private String notice_type;
    private Sender sender;
    private List<ReceiveMessageSegment> message;
    private String raw_message;
    private String post_type;
    private String group_id;
    private Integer font;
    private String sub_type;
    private String message_format;
    private String real_id;
    private String message_seq;
    private String target_id;
}