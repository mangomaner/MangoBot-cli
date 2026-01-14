package io.github.mangomaner.mangobot.model.onebot.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@JsonDeserialize(using = JsonDeserializer.None.class)
public abstract class MetaEvent extends BaseEvent {
    @JsonProperty("meta_event_type")
    private String metaEventType;
}
