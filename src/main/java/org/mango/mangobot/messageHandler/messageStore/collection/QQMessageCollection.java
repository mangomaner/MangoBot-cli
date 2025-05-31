package org.mango.mangobot.messageHandler.messageStore.collection;

import lombok.Data;
import org.mango.mangobot.model.QQ.ReceiveMessageSegment;

import java.util.List;

/**
 * 存入数据库的数据结构
 */
@Data
public class QQMessageCollection {
    /**
     * 号主QQ的id
     */
    private String self_id;
    /**
     * 消息发送者id
     */
    private String user_id;
    /**
     * 发送时间
     */
    private Long time;
    /**
     * 解析后的消息（包含at、文本、图片等）
     */
    private List<ReceiveMessageSegment> message;
    /**
     * 是否撤回
     */
    private Boolean isDelete = false;
}
