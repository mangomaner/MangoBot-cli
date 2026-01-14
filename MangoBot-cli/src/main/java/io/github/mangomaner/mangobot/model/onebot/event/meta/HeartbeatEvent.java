package io.github.mangomaner.mangobot.model.onebot.event.meta;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import io.github.mangomaner.mangobot.model.onebot.event.MetaEvent;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@JsonTypeName("heartbeat")
public class HeartbeatEvent extends MetaEvent {
    private long interval;
    private Status status;

    @Data
    public static class Status {
        private boolean online;
        private boolean good;
    }
}
