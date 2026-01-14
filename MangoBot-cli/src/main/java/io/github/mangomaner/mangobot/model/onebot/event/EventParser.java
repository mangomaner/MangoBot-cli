package io.github.mangomaner.mangobot.model.onebot.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class EventParser {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static Event parse(String json) throws JsonProcessingException {
        return objectMapper.readValue(json, io.github.mangomaner.mangobot.model.onebot.event.BaseEvent.class);
    }
}
