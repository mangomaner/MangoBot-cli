package io.github.mangomaner.mangobot.model.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;

/**
 * 
 * @TableName group_messages
 */
@TableName(value ="group_messages")
@Data
public class GroupMessages implements Serializable {
    /**
     * 
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 
     */
    private Long botId;

    /**
     * 
     */
    private Long groupId;

    /**
     * 
     */
    private Integer messageId;

    /**
     * 
     */
    private Long senderId;

    /**
     * 
     */
    private String messageSegments;

    /**
     * 
     */
    private Long messageTime;

    @TableLogic
    private Integer isDelete;

    /**
     * 
     */
    private String parseMessage;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

    @Override
    public boolean equals(Object that) {
        if (this == that) {
            return true;
        }
        if (that == null) {
            return false;
        }
        if (getClass() != that.getClass()) {
            return false;
        }
        GroupMessages other = (GroupMessages) that;
        return (this.getId() == null ? other.getId() == null : this.getId().equals(other.getId()))
            && (this.getBotId() == null ? other.getBotId() == null : this.getBotId().equals(other.getBotId()))
            && (this.getGroupId() == null ? other.getGroupId() == null : this.getGroupId().equals(other.getGroupId()))
            && (this.getMessageId() == null ? other.getMessageId() == null : this.getMessageId().equals(other.getMessageId()))
            && (this.getSenderId() == null ? other.getSenderId() == null : this.getSenderId().equals(other.getSenderId()))
            && (this.getMessageSegments() == null ? other.getMessageSegments() == null : this.getMessageSegments().equals(other.getMessageSegments()))
            && (this.getMessageTime() == null ? other.getMessageTime() == null : this.getMessageTime().equals(other.getMessageTime()))
            && (this.getParseMessage() == null ? other.getParseMessage() == null : this.getParseMessage().equals(other.getParseMessage()));
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((getId() == null) ? 0 : getId().hashCode());
        result = prime * result + ((getBotId() == null) ? 0 : getBotId().hashCode());
        result = prime * result + ((getGroupId() == null) ? 0 : getGroupId().hashCode());
        result = prime * result + ((getMessageId() == null) ? 0 : getMessageId().hashCode());
        result = prime * result + ((getSenderId() == null) ? 0 : getSenderId().hashCode());
        result = prime * result + ((getMessageSegments() == null) ? 0 : getMessageSegments().hashCode());
        result = prime * result + ((getMessageTime() == null) ? 0 : getMessageTime().hashCode());
        result = prime * result + ((getParseMessage() == null) ? 0 : getParseMessage().hashCode());
        return result;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName());
        sb.append(" [");
        sb.append("Hash = ").append(hashCode());
        sb.append(", id=").append(id);
        sb.append(", botId=").append(botId);
        sb.append(", groupId=").append(groupId);
        sb.append(", messageId=").append(messageId);
        sb.append(", senderId=").append(senderId);
        sb.append(", messageSegments=").append(messageSegments);
        sb.append(", messageTime=").append(messageTime);
        sb.append(", parseMessage=").append(parseMessage);
        sb.append(", serialVersionUID=").append(serialVersionUID);
        sb.append("]");
        return sb.toString();
    }
}