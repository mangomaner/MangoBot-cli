package io.github.mangomaner.mangobot.adapter.onebot.model.event.meta;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.github.mangomaner.mangobot.adapter.onebot.model.event.OneBotBaseEvent;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public abstract class OneBotMetaEvent extends OneBotBaseEvent {
    @JsonProperty("meta_event_type")
    private String metaEventType;
}
