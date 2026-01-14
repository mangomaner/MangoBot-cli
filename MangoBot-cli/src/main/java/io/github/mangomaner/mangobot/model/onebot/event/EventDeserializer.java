package io.github.mangomaner.mangobot.model.onebot.event;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import io.github.mangomaner.mangobot.model.onebot.event.message.GroupMessageEvent;
import io.github.mangomaner.mangobot.model.onebot.event.message.PrivateMessageEvent;
import io.github.mangomaner.mangobot.model.onebot.event.meta.HeartbeatEvent;
import io.github.mangomaner.mangobot.model.onebot.event.meta.LifecycleEvent;
import io.github.mangomaner.mangobot.model.onebot.event.notice.*;

import java.io.IOException;

public class EventDeserializer extends StdDeserializer<Event> {

    public EventDeserializer() {
        this(null);
    }

    public EventDeserializer(Class<?> vc) {
        super(vc);
    }

    @Override
    public Event deserialize(JsonParser p, DeserializationContext ctxt) throws IOException{
        ObjectMapper mapper = (ObjectMapper) p.getCodec();
        JsonNode node = mapper.readTree(p);
        
        if (!node.has("post_type")) {
            return null;
        }
        
        String postType = node.get("post_type").asText();
        Class<? extends Event> targetClass = null;

        switch (postType) {
            case "message":
                if (node.has("message_type")) {
                    String messageType = node.get("message_type").asText();
                    if ("group".equals(messageType)) {
                        targetClass = GroupMessageEvent.class;
                    } else if ("private".equals(messageType)) {
                        targetClass = PrivateMessageEvent.class;
                    }
                }
                break;
            case "meta_event":
                if (node.has("meta_event_type")) {
                    String metaType = node.get("meta_event_type").asText();
                    if ("heartbeat".equals(metaType)) {
                        targetClass = HeartbeatEvent.class;
                    } else if ("lifecycle".equals(metaType)) {
                        targetClass = LifecycleEvent.class;
                    }
                }
                break;
            case "notice":
                if (node.has("notice_type")) {
                    String noticeType = node.get("notice_type").asText();
                    switch (noticeType) {
                        case "notify":
                            targetClass = PokeEvent.class;
                            break;
                        case "group_decrease":
                            targetClass = GroupDecreaseEvent.class;
                            break;
                        case "group_increase":
                            targetClass = GroupIncreaseEvent.class;
                            break;
                        case "group_ban":
                            targetClass = GroupBanEvent.class;
                            break;
                        case "essence":
                            targetClass = EssenceEvent.class;
                            break;
                        case "group_recall":
                            targetClass = GroupRecallEvent.class;
                            break;
                    }
                }
                break;
        }

        if (targetClass != null) {
            return mapper.treeToValue(node, targetClass);
        }
        
        // Fallback or ignore
        return null;
    }
}
