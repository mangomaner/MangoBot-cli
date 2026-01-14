package io.github.mangomaner.mangobot.model.onebot.event.message;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import io.github.mangomaner.mangobot.model.onebot.event.MessageEvent;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@JsonTypeName("group")
public class GroupMessageEvent extends MessageEvent {
    @JsonProperty("group_id")
    private long groupId;

    @JsonProperty("group_name")
    private String groupName;
}
