package org.mango.mangobot.manager.websocketReverseProxy.dispatcher;

import org.mango.mangobot.model.QQ.QQMessage;
import org.mango.mangobot.model.QQ.ReceiveMessageSegment;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class AnnotationUtils {

    // 根据消息类型获取全部的消息段类型（即从QQMessage提取有效信息）
    public static Set<String> getSegmentTypes(QQMessage message) {
        return Optional.ofNullable(message.getMessage())
                .map(list -> list.stream()
                        .map(ReceiveMessageSegment::getType)
                        .collect(Collectors.toSet()))
                .orElse(Collections.emptySet());
    }

    // 判断QQMessage中是否包含指定类型的消息段
    public static boolean containsSegmentOfType(QQMessage message, String type) {
        return Optional.ofNullable(message.getMessage())
                .stream()
                .flatMap(List::stream)
                .anyMatch(seg -> type.equalsIgnoreCase(seg.getType()));
    }

    // 验证QQMessage中的消息段是否合法（针对全匹配项）
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

    public static boolean isAtSelf(QQMessage message, List<ReceiveMessageSegment> segments, String selfQQ) {
        for (ReceiveMessageSegment segment : segments) {
            if ("at".equalsIgnoreCase(segment.getType())) {
                String atQQStr = segment.getData().getQq();
                if (atQQStr != null && atQQStr.equals(selfQQ)) {
                    return true;
                }
                break;
            }
        }
        return false;
    }
}