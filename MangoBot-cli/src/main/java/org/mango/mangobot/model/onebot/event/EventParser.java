package org.mango.mangobot.model.onebot.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

public class EventParser {
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public static Event parse(String json) throws JsonProcessingException {
        return objectMapper.readValue(json, BaseEvent.class);
    }
}
