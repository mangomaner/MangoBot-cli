package org.mango.mangobot.manager.websocketReverseProxy.dispatcher.impl;

import org.mango.mangobot.model.QQ.QQMessage;
import org.mango.mangobot.model.QQ.ReceiveMessageSegment;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class AnnotationUtils {

    public static Set<String> getSegmentTypes(QQMessage message) {
        return Optional.ofNullable(message.getMessage())
                .map(list -> list.stream()
                        .map(ReceiveMessageSegment::getType)
                        .collect(Collectors.toSet()))
                .orElse(Collections.emptySet());
    }

    public static boolean containsSegmentOfType(QQMessage message, String type) {
        return Optional.ofNullable(message.getMessage())
                .stream()
                .flatMap(List::stream)
                .anyMatch(seg -> type.equalsIgnoreCase(seg.getType()));
    }

    public static boolean validateSegmentsForAtTextImageReply(List<ReceiveMessageSegment> segments) {
        if (segments == null) return false;

        for (ReceiveMessageSegment segment : segments) {
            String type = segment.getType();
            if (!"text".equals(type) && !"image".equals(type) &&
                    !"reply".equals(type) && !"at".equals(type)) {
                return false;
            }
        }

        return true;
    }
}