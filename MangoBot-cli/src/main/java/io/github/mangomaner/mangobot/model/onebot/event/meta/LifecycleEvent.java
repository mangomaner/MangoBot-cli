package io.github.mangomaner.mangobot.model.onebot.event.meta;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import io.github.mangomaner.mangobot.model.onebot.event.MetaEvent;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@JsonTypeName("lifecycle")
public class LifecycleEvent extends MetaEvent {
    @JsonProperty("sub_type")
    private String subType; // connect, etc.
}
