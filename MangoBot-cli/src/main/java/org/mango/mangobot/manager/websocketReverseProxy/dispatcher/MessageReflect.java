package org.mango.mangobot.manager.websocketReverseProxy.dispatcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.mango.mangobot.annotation.QQ.QQMessageHandlerType;
import org.mango.mangobot.annotation.QQ.method.*;
import org.mango.mangobot.manager.websocketReverseProxy.dispatcher.impl.AnnotationUtils;
import org.mango.mangobot.manager.websocketReverseProxy.dispatcher.impl.HandlerMatcherDispatcher;
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
    private HandlerMatcherDispatcher handlerMatcherDispatcher;
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
        String noticeType = (String) messageMap.get("notice_type");
        if ("group_recall".equals(noticeType)) { // 撤回消息
            Long messageId = ((Number) messageMap.get("message_id")).longValue();
            Long groupId = ((Number) messageMap.get("group_id")).longValue();
            try {
                chatMessageStoreService.updateRecallStatus(groupId, messageId);
            } catch (Exception e) {
                log.error("处理撤回通知失败", e);
            }
        } else if ("notify".equals(noticeType)) { // 戳一戳等通知
            QQMessage qqMessage = objectMapper.convertValue(messageMap, QQMessage.class);
            dispatchNotifyMessage(qqMessage);
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
     * 调用完全匹配的消息处理方法。首先遍历每个方法，取出每个方法上的所有注解
     * 第一阶段：匹配普通注解（@AtMessage、@TextMessage等），要求注解数量 == segment 类型数量（@PokeMessage 单独处理）
     * 第二阶段：仅当第一阶段无匹配时，尝试匹配 @AtTextImageReplyMessage
     */
    private void invokeHandlers(QQMessage message, boolean isSelfAt) {
        Method matchedMethod = null;

        Set<String> segmentTypes = AnnotationUtils.getSegmentTypes(message);

        // ===== 第一阶段：匹配普通注解 =====
        for (Map.Entry<Method, List<Annotation>> entry : messageHandlers.entrySet()) {
            Method method = entry.getKey();
            List<Annotation> annotations = entry.getValue();

            if (annotations.isEmpty()) continue;

            // 特殊处理 @PokeMessage：只能单独使用
            if (annotations.size() == 1 && annotations.get(0) instanceof PokeMessage) {
                if (!"poke".equalsIgnoreCase(message.getSub_type())) continue;
                if (message.getMessage() != null && !message.getMessage().isEmpty()) continue;
            } else {
                // 正常情况：注解数量必须等于 segment 类型种类数
                if (annotations.size() != segmentTypes.size()) continue;
            }

            // 使用策略模式判断是否匹配（重中之重，核心逻辑在此）！！！
            boolean allMatch = annotations.stream()
                    .allMatch(annotation -> handlerMatcherDispatcher.matches(annotation, message, isSelfAt));

            if (allMatch) {
                matchedMethod = method;
                break;
            }
        }

        if (matchedMethod != null) {
            invokeMethod(matchedMethod, message);
            return;
        }

        // ===== 第二阶段：尝试匹配 @AtTextImageReplyMessage =====
        for (Map.Entry<Method, List<Annotation>> entry : messageHandlers.entrySet()) {
            Method method = entry.getKey();
            List<Annotation> annotations = entry.getValue();

            if (annotations.isEmpty()) continue;

            if (annotations.stream().noneMatch(a -> a instanceof AtTextImageReplyMessage)) continue;

            if (!AnnotationUtils.validateSegmentsForAtTextImageReply(message.getMessage())) continue;

            matchedMethod = method;
            break;
        }

        if (matchedMethod != null) {
            invokeMethod(matchedMethod, message);
        } else {
            log.debug("没有找到匹配的消息处理器");
        }
    }

    /**
     * 封装方法调用逻辑
     */
    private void invokeMethod(Method method, QQMessage message) {
        Object[] args = resolveParameters(method, message);
        try {
            method.setAccessible(true);
            method.invoke(groupMessageHandler, args);
        } catch (Exception e) {
            log.error("调用方法失败: {}", method.getName(), e);
        }
    }

    /**
     * 参数解析器
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