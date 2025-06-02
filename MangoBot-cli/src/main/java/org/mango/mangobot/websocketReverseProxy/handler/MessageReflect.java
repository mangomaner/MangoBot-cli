package org.mango.mangobot.websocketReverseProxy.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.mango.mangobot.QQ.QQMessageHandlerType;
import org.mango.mangobot.QQ.method.*;
import org.mango.mangobot.messageHandler.GroupMessageHandler;
import org.mango.mangobot.messageHandler.messageStore.ChatMessageStoreService;
import org.mango.mangobot.model.QQ.QQMessage;
import org.mango.mangobot.model.QQ.ReceiveMessageSegment;
import org.mango.mangobot.utils.MethodParameter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 消息处理器类，负责接收并处理QQ消息事件（如普通消息、通知等）。
 * 支持根据消息类型分发给 GroupMessageHandler 中带有特定注解的方法进行处理，
 * 同时将消息存储到 MongoDB 数据库中。
 */
@Component
@Slf4j
public class MessageReflect {

    @Value("${QQ.botQQ}")
    String selfQQ;
    /**
     * 群消息处理器，包含多个被注解标记的消息处理方法
     */
    @Resource
    private GroupMessageHandler groupMessageHandler;
    @Resource
    private ChatMessageStoreService chatMessageStoreService;
    /**
     * 参数解析器列表，Spring 会自动注入所有实现了 ArgumentResolver 接口的 Bean
     */
    @Autowired
    private List<ParameterArgumentResolver> parameterArgumentResolvers;
    /**
     * 保存注解类型与其对应方法的映射关系，便于后续消息分发
     */
    private final Map<Method, List<Annotation>> messageHandlers = new HashMap<>();
    @Resource
    private ObjectMapper objectMapper;
    /**
     * 初始化方法，在 Spring 完成依赖注入后执行。
     * 扫描 GroupMessageHandler 类中的所有方法，并注册带有指定注解的方法。
     */
    @PostConstruct
    public void init() {
        Method[] methods = GroupMessageHandler.class.getDeclaredMethods();
        for (Method method : methods) {
            List<Annotation> annotations = Arrays.stream(method.getAnnotations())
                    .filter(annotation -> annotation.annotationType().isAnnotationPresent(QQMessageHandlerType.class))
                    .collect(Collectors.toList());

            if (!annotations.isEmpty()) {
                messageHandlers.put(method, annotations);
                log.info("注册方法: {} 带注解: {}", method.getName(), annotations.stream().map(Annotation::annotationType).map(Class::getSimpleName).collect(Collectors.joining(", ")));
            }
        }
        log.info("成功注册 {} 个带注解的消息处理方法", messageHandlers.size());
    }

    /**
     * 处理普通消息事件，将其保存到 MongoDB 并转发给对应的处理器方法
     *
     * @param messageMap 包含消息内容的 Map
     */
    public void handleMessageEvent(Map<String, Object> messageMap) {
        try {
            // 将 Map 转换为 QQMessage 对象
            QQMessage qqMessage = objectMapper.convertValue(messageMap, QQMessage.class);

            String groupId = qqMessage.getGroup_id();

            if (groupId == null || groupId.isEmpty()) {
                log.warn("无法获取有效的 group_id，消息将不会被存储");
                return;
            }

            // 分发消息到相应的处理方法
            dispatchMessage(qqMessage);

            // 存储消息到 MongoDB
            chatMessageStoreService.saveMessageToDatabase(qqMessage, groupId);

        } catch (Exception e) {
            log.error("处理消息事件失败", e);
        }
    }

    /**
     * 处理通知事件（如撤回消息）
     *
     * @param messageMap 包含通知信息的 Map
     */
    public void handleNoticeEvent(Map<String, Object> messageMap) {
        try {
            String noticeType = (String) messageMap.get("notice_type");

            if ("group_recall".equals(noticeType)) { // 撤回消息
                Long messageId = ((Number) messageMap.get("message_id")).longValue();
                Long groupId = ((Number) messageMap.get("group_id")).longValue();

                chatMessageStoreService.updateRecallStatus(groupId, messageId);
            } else if ("notify".equals(noticeType)) { // 戳一戳等通知
                QQMessage qqMessage = objectMapper.convertValue(messageMap, QQMessage.class);
                dispatchNotifyMessage(qqMessage);
            }
        } catch (Exception e) {
            log.error("处理撤回通知失败", e);
        }
    }

    /**
     * 处理发送消息后的 Echo 事件，将 MongoDB 中 id 为 echo 的消息更新为 messageId
     * @param messageMap
     */
    public void handleEchoEvent(Map<String, Object> messageMap) {
        String echo = (String) messageMap.get("echo");
        Long messageId = Optional.ofNullable(messageMap)
                .map(map -> (Map<String, Object>) map.get("data"))
                .map(data -> data.get("message_id"))
                .map(obj -> ((Number) obj).longValue()) // 安全地转为 long
                .map(Long::valueOf)
                .orElse(null);

        if (echo != null && messageId != null) {
            chatMessageStoreService.updateSentMessageEchoId(echo, messageId);
        } else {
            log.warn("缺少必要字段，无法更新消息 ID");
        }
    }

    /**
     * 判断是否是 at 自己，然后调用 invokeHandlers 遍历所有方法
     */
    private void dispatchMessage(QQMessage message) {
        boolean isSelfAt = false;

        List<ReceiveMessageSegment> segments = message.getMessage();
        if (segments != null) {
            for (ReceiveMessageSegment segment : segments) {
                if ("at".equalsIgnoreCase(segment.getType())) {
                    String atQQStr = segment.getData().getQq();
                    if (atQQStr != null && atQQStr.equals(selfQQ)) {
                        isSelfAt = true;
                    }
                    break;
                }
            }
        }

        invokeHandlers(message, isSelfAt);
    }

    private void dispatchNotifyMessage(QQMessage message) {
        if ("poke".equals(message.getSub_type())) {
            invokeHandlers(message, false); // 不涉及 at 自己
        }
    }

    /**
     * 调用所有带有指定注解的消息处理方法。
     * 该方法会根据消息内容解析每个方法所需的参数，并通过反射调用对应的方法。
     * todo 待优化
     */
    private void invokeHandlers(QQMessage message, boolean isSelfAt) {
        Method bestMatch = null;
        int bestScore = -1;

        for (Map.Entry<Method, List<Annotation>> entry : messageHandlers.entrySet()) {
            Method method = entry.getKey();
            List<Annotation> annotations = entry.getValue();

            if (annotations.isEmpty()) continue;

            // 如果有一个消息与注解匹配不上，就为false，表示不执行该方法
            boolean allAnnotationsMatch = true;

            // 判断消息中的 segment类型数量 是否与 注解类型数量 匹配
            List<ReceiveMessageSegment> segments = message.getMessage();
            Set<String> segmentTypes = null;
            if(segments != null) {
                segmentTypes = segments.stream().map(ReceiveMessageSegment::getType).collect(Collectors.toSet());
                // 规则：优先级最低，若已有bestMatch则直接跳过，若没有且符合规则就暂时标为bestMatch并继续循环
                if (annotations.size() == 1 && bestMatch == null) {
                    // 匹配 @TextImageReplyMessage
                    if (annotations.get(0) instanceof AtTextImageReplyMessage) {
                        boolean hasRightSegement = true;
                        assert segments != null;
                        for (ReceiveMessageSegment segment : segments) {
                            if (segment.getType().equals("text")) {
                            } else if (segment.getType().equals("image")) {
                            } else if (segment.getType().equals("reply")) {
                            } else if (segment.getType().equals("at")) {
                            } else {
                                hasRightSegement = false;
                                break;
                            }
                        }
                        // 如果满足条件
                        if (hasRightSegement) {
                            bestMatch = method;
                            continue;
                        }
                    }
                }
                if (segmentTypes != null && annotations.size() != segmentTypes.size()) {
                    continue;
                }
            }

            for (Annotation annotation : annotations) {

                if (annotation instanceof AtMessage atAnno) {
                    // 判断是否要求 @ 自己
                    if (atAnno.self() != isSelfAt) {
                        allAnnotationsMatch = false;
                        break;
                    }

                    // 检查是否有 at 类型的 segment
                    boolean hasAtSegment = containsSegmentOfType(message, "at");
                    if (!hasAtSegment) {
                        allAnnotationsMatch = false;
                        break;
                    }
                } else if (annotation instanceof TextMessage) {
                    boolean hasTextSegment = containsSegmentOfType(message, "text");
                    if (!hasTextSegment) {
                        allAnnotationsMatch = false;
                        break;
                    }
                } else if (annotation instanceof ImageMessage) {
                    boolean hasImageSegment = containsSegmentOfType(message, "image");
                    if (!hasImageSegment) {
                        allAnnotationsMatch = false;
                        break;
                    }
                } else if (annotation instanceof PokeMessage) {
                    // Poke 消息没有 message 字段，直接看 sub_type
                    if (!"poke".equalsIgnoreCase(message.getSub_type())) {
                        allAnnotationsMatch = false;
                        break;
                    }
                } else if (annotation instanceof ReplyMessage) {
                    boolean hasReplySegment = containsSegmentOfType(message, "reply");
                    if (!hasReplySegment) {
                        allAnnotationsMatch = false;
                        break;
                    }
                }
            }

            if (allAnnotationsMatch) {
                int score = calculateMatchScore(method, annotations);
                if (score > bestScore) {
                    bestScore = score;
                    bestMatch = method;
                }
            }
        }

        // 调用最匹配的方法
        if (bestMatch != null) {
            Object[] args = resolveParameters(bestMatch, message);
            try {
                bestMatch.setAccessible(true);
                bestMatch.invoke(groupMessageHandler, args);
            } catch (Exception e) {
                log.error("调用方法失败: {}", bestMatch.getName(), e);
            }
        }
    }
    private boolean containsSegmentOfType(QQMessage message, String type) {
        if (message.getMessage() == null || message.getMessage().isEmpty()) {
            return false;
        }

        return message.getMessage().stream()
                .anyMatch(seg -> type.equalsIgnoreCase(seg.getType()));
    }
    private int calculateMatchScore(Method method, List<Annotation> annotations) {
        int score = 0;
        // 注解越多越具体
        score += annotations.size() * 10;
        return score;
    }
    /**
     * 解析方法参数，使用 ArgumentResolver 提供具体实现
     */
    private Object[] resolveParameters(Method method, QQMessage message) {
        Object[] args = new Object[method.getParameterCount()];
        for (int i = 0; i < method.getParameterCount(); i++) {
            Class<?> paramType = method.getParameterTypes()[i];
            Annotation[] annotations = method.getParameterAnnotations()[i];

            MethodParameter parameter = new MethodParameter(paramType, annotations, i);

            for (ParameterArgumentResolver resolver : parameterArgumentResolvers) {
                if (resolver.supportsParameter(parameter)) {
                    try {
                        args[i] = resolver.resolveArgument(parameter, message);
                    } catch (Exception e) {
                        log.error("参数解析失败", e);
                    }
                    break;
                }
            }
        }
        return args;
    }
}