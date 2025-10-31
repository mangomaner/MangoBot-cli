package org.mango.mangobot.manager.websocketReverseProxy.dispatcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.mango.mangobot.annotation.PluginPriority;
import org.mango.mangobot.annotation.QQ.QQMessageHandlerType;
import org.mango.mangobot.annotation.QQ.method.*;
import org.mango.mangobot.manager.websocketReverseProxy.dispatcher.impl.ParameterArgumentResolver;
import org.mango.mangobot.manager.websocketReverseProxy.dispatcher.impl.parameter.*;
import org.mango.mangobot.messageHandler.GroupMessageHandler;
import org.mango.mangobot.model.dto.handler.ChatMessageDTO;
import org.mango.mangobot.messageHandler.messageStore.ChatMessageStoreService;
import org.mango.mangobot.model.QQ.QQMessage;
import org.mango.mangobot.plugin.PluginLoadEvent;
import org.mango.mangobot.plugin.RegisteredHandler;
import org.mango.mangobot.utils.MethodParameter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.context.event.EventListener;
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
    @Resource
    private ChatMessageStoreService chatMessageStoreService;
    /**
     * 参数解析器列表，Spring 会自动注入所有实现了 ParameterArgumentResolver 接口的 Bean，注意要用Autowired类型匹配，不可用Resource
     */
    @Autowired
    private List<ParameterArgumentResolver> parameterArgumentResolvers;
    /**
     * 保存注解类型与其对应方法的映射关系，便于后续消息分发
     */
    @Resource
    private Map<Method, RegisteredHandler> messageHandlers;
    @Resource
    private ObjectMapper objectMapper;
    // 核心缓存：segmentTypes -> 最优处理器
    private final Map<Set<String>, RegisteredHandler> handlerCache = new ConcurrentHashMap<>();

    // 特殊缓存：@AtTextImageReplyMessage 单独处理
    private RegisteredHandler atTextImageReplyHandler;
    @Resource
    private GroupMessageHandler groupMessageHandler;
    @Resource
    private Map<Method, RegisteredHandler> messageHandler;
    @Resource
    private HandlerMatcherDispatcher handlerMatcherDispatcher;

    // 在 MessageReflect 类中
    @EventListener
    public void handlePluginLoad(PluginLoadEvent event) {
        initHandlerCache(); // 调用上面定义的缓存构建方法
    }
    public void initHandlerCache() {
        log.info("开始初始化消息处理器缓存...");

        for (Method method : groupMessageHandler.getClass().getDeclaredMethods()) {
            if (isMessageHandlerMethod(method)) {
                int priority = 100;
                if(method.isAnnotationPresent(PluginPriority.class)){
                    priority = method.getAnnotation(PluginPriority.class).value();
                }
                List<Annotation> messageAnnotations = List.of(method.getAnnotations());
                // 获取当前方法所有支持的消息类型
                Set<String> segmentTypes = messageAnnotations.stream()
                        .filter(a -> a.annotationType().isAnnotationPresent(QQMessageHandlerType.class))
                        .map(a -> handlerMatcherDispatcher.getHandlerMatcher(a).getSupportMessageType().getValue())
                        .collect(Collectors.toSet());
                messageHandler.putIfAbsent(method, new RegisteredHandler(method, groupMessageHandler, messageAnnotations, priority, segmentTypes));
            }
        }

        List<RegisteredHandler> normalCandidates = new ArrayList<>();
        atTextImageReplyHandler = null;

        // 遍历所有注册的处理器
        for (Map.Entry<Method, RegisteredHandler> entry : messageHandlers.entrySet()) {
            RegisteredHandler register = entry.getValue();
            // 拿到处理器的所有注解
            List<Annotation> annotations = register.getAnnotations();

            if (annotations.isEmpty()) continue;

            // 1. 处理 @AtTextImageReplyMessage（单独缓存）
            if (annotations.stream().anyMatch(a -> a instanceof DefaultMessage)) {
                atTextImageReplyHandler = register;
                continue;
            }

            // 2. 处理普通注解（@AtMessage, @TextMessage 等）
            // 特殊：@PokeMessage 只能单独使用
            if (annotations.size() == 1 && annotations.get(0) instanceof PokeMessage) {
                Set<String> key = Set.of("poke");
                handlerCache.put(key, register);
                continue;
            }

            // 过滤掉非消息类型的注解（如 @PluginPriority）
            List<Annotation> messageAnnotations = annotations.stream()
                    .filter(a -> !(a instanceof PluginPriority))
                    .toList();

            if (messageAnnotations.isEmpty()) continue;

            // 先暂存，后面按优先级合并
            normalCandidates.add(register);
        }

        // 按优先级排序，合并相同 key 的处理器（保留优先级最高的）
        for(RegisteredHandler register : normalCandidates){
            Set<String> set = register.getSegmentTypes();
            if(handlerCache.containsKey(set)){
                handlerCache.put(set, handlerCache.get(set).getPriority() < register.getPriority() ? handlerCache.get(set) : register);
            } else {
                handlerCache.put(set, register);
            }
        }
        log.info("消息处理器缓存初始化完成，共缓存 {} 个普通处理器，{} 个 Default 处理器",
                handlerCache.size(), atTextImageReplyHandler != null ? 1 : 0);
    }
    private boolean isMessageHandlerMethod(Method method) {
        return method.isAnnotationPresent(TextMessage.class)
                || method.isAnnotationPresent(AtMessage.class)
                || method.isAnnotationPresent(PokeMessage.class)
                || method.isAnnotationPresent(AudioMessage.class)
                || method.isAnnotationPresent(ImageMessage.class)
                || method.isAnnotationPresent(ReplyMessage.class)
                || method.isAnnotationPresent(DefaultMessage.class);
    }
    /**
     * 处理普通消息事件
     */
    public void handleMessageEvent(Map<String, Object> messageMap) {
        try {
            QQMessage qqMessage = objectMapper.convertValue(messageMap, QQMessage.class);
            String groupId = qqMessage.getGroup_id();

            if (groupId == null || groupId.isEmpty()) {
                log.warn("无法获取有效的 group_id，消息将不会被存储");
                return;
            }

            // 使用缓存分发（核心优化）
            dispatchMessageWithCache(qqMessage);

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
    private void dispatchNotifyMessage(QQMessage message) {
        if ("poke".equals(message.getSub_type())) {
            RegisteredHandler pokeHandler = handlerCache.get(Set.of("poke"));
            invokeMethod(pokeHandler, message);
            // invokeHandlers(message, false); // 不涉及 at 自己
        }
    }

    /**
     * 处理发送消息后的 Echo 事件，将 DB 中 id 为 echo 的消息更新为 messageId
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
     * 使用缓存分发消息（O(1) 查找）
     */
    private void dispatchMessageWithCache(QQMessage message) {
        // boolean isSelfAt = AnnotationUtils.isAtSelf(message, message.getMessage(), selfQQ);
        Set<String> segmentTypes = AnnotationUtils.getSegmentTypes(message);

        // ===== 第一阶段：查普通缓存 =====
        RegisteredHandler matched = handlerCache.get(segmentTypes);
        if (matched != null) {
            invokeMethod(matched, message);
            return;
        }

        // ===== 第二阶段：检查 @AtTextImageReplyMessage =====
        if (atTextImageReplyHandler != null && AnnotationUtils.validateSegmentsForAtTextImageReply(message.getMessage())) {
            invokeMethod(atTextImageReplyHandler, message);
            return;
        }

        log.debug("没有找到匹配的消息处理器，segmentTypes: {}", segmentTypes);
    }

    /**
     * 封装方法调用逻辑
     */
    private void invokeMethod(RegisteredHandler register, QQMessage message) {
//        Object[] args = resolveParameters(register.getMethod(), message);
        ChatMessageDTO args = resolveParameter(register.getMethod(), message);
        try {
            register.getMethod().setAccessible(true);
            register.getMethod().invoke(register.getHandlerInstance(), args);
        } catch (Exception e) {
            log.error("调用方法失败: {}", register.getMethod().getName(), e);
        }
    }

    /**
     * 参数解析器
     */
    private ChatMessageDTO resolveParameter(Method method, QQMessage message) {

        ChatMessageDTO chatMessageDTO = new ChatMessageDTO();
        chatMessageDTO.setMessageId(message.getMessage_id());
        chatMessageDTO.setMessage(new ContentParameterArgumentResolver().resolveArgument(null, message).toString());
        chatMessageDTO.setImageUrl(new ImageURLParameterArgumentResolver().resolveArgument(null, message).toString());
        chatMessageDTO.setFile(new ImageFileParameterArgumentResolver().resolveArgument(null, message).toString());
        chatMessageDTO.setTimestamp(message.getTime());
        chatMessageDTO.setGroupId(message.getGroup_id());
        chatMessageDTO.setUserId(message.getUser_id());
        chatMessageDTO.setTargetId(new TargetIdParameterArgumentResolver().resolveArgument(null, message).toString());
        chatMessageDTO.setReceiveMessageSegment(message.getMessage());

        return chatMessageDTO;
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


//    /**
//     * 处理普通消息事件，将其保存到 MongoDB 并转发给对应的处理器方法
//     *
//     * @param messageMap 包含消息内容的 Map
//     */
//    public void handleMessageEventOld(Map<String, Object> messageMap) {
//        try {
//            // 将 Map 转换为 QQMessage 对象
//            QQMessage qqMessage = objectMapper.convertValue(messageMap, QQMessage.class);
//
//            String groupId = qqMessage.getGroup_id();
//
//            if (groupId == null || groupId.isEmpty()) {
//                log.warn("无法获取有效的 group_id，消息将不会被存储");
//                return;
//            }
//
//            // 分发消息到相应的处理方法
//            dispatchMessage(qqMessage);
//
//            // 存储消息到 MongoDB
//            chatMessageStoreService.saveMessageToDatabase(qqMessage, groupId);
//
//        } catch (Exception e) {
//            log.error("处理消息事件失败", e);
//        }
//    }
//    /**
//     * 判断是否是 at 自己，然后调用 invokeHandlers 遍历所有方法
//     */
//    private void dispatchMessage(QQMessage message) {
//        boolean isSelfAt = false;
//
//        List<ReceiveMessageSegment> segments = message.getMessage();
//        if (segments != null) {
//            for (ReceiveMessageSegment segment : segments) {
//                if ("at".equalsIgnoreCase(segment.getType())) {
//                    String atQQStr = segment.getData().getQq();
//                    if (atQQStr != null && atQQStr.equals(selfQQ)) {
//                        isSelfAt = true;
//                    }
//                    break;
//                }
//            }
//        }
//
//        invokeHandlers(message, isSelfAt);
//    }
//
//    /**
//     * 遍历方法
//     * 1. 根据方法上的注解获取该方法对应的参数类型集合
//     * 2. 将对应的 参数类型集合 和 方法 包装为map
//     */
//    private void invokeHandlersV2(){
//        // 1.
//        Map<Set<String>, Method> findMatchedMethod = new HashMap<>();
//        for (Map.Entry<Method, RegisteredHandler> entry : messageHandlers.entrySet()) {
//            List<Annotation> annotations = entry.getValue().getAnnotations();
//            List<HandlerMatcher> handlerMatchers = annotations.stream()
//                    .map(a->handlerMatcherDispatcher.getHandlerMatcher(a))
//                    .toList();
//            List<String> segmentTypes = handlerMatchers.stream()
//                    .map(handlerMatcher->handlerMatcher.getSupportMessageType().getValue())
//                    .toList();
//            findMatchedMethod.put(new HashSet<>(segmentTypes), entry.getValue().getMethod());
//        }
//    }
//
//    /**
//     * 调用完全匹配的消息处理方法。首先遍历每个方法，取出每个方法上的所有注解
//     * 第一阶段：匹配普通注解（@AtMessage、@TextMessage等），要求注解数量 == segment 类型数量（@PokeMessage 单独处理）
//     * 第二阶段：仅当第一阶段无匹配时，尝试匹配 @AtTextImageReplyMessage
//     */
//    private void invokeHandlers(QQMessage message, boolean isSelfAt) {
//        RegisteredHandler matchedRegister = null;
//
//        Set<String> segmentTypes = AnnotationUtils.getSegmentTypes(message);
//
//        // ===== 第一阶段：匹配普通注解 =====
//        List<RegisteredHandler> candidates = new ArrayList<>();
//
//        for (Map.Entry<Method, RegisteredHandler> entry : messageHandlers.entrySet()) {
//            RegisteredHandler register = entry.getValue();
//            List<Annotation> annotations = entry.getValue().getAnnotations();
//
//            if (annotations.isEmpty()) continue;
//
//            // 特殊处理 @PokeMessage：只能单独使用
//            if (annotations.size() == 1 && annotations.get(0) instanceof PokeMessage) {
//                if (!"poke".equalsIgnoreCase(message.getSub_type())) continue;
//                if (message.getMessage() != null && !message.getMessage().isEmpty()) continue;
//            } else {
//                // 正常情况：注解数量必须等于 segment 类型种类数
//                annotations = annotations.stream()
//                        .filter(a -> !(a instanceof PluginPriority))
//                        .toList();
//
//                if (annotations.size() != segmentTypes.size()) continue;
//            }
//
//            // 使用策略模式判断是否匹配（重中之重，核心逻辑在此）！！！
//            boolean allMatch = annotations.stream()
//                    .allMatch(annotation -> handlerMatcherDispatcher.matches(annotation, message, isSelfAt));
//
//            if (allMatch) {
//                candidates.add(register);
//            }
//        }
//
//        // 按优先级排序（从小到大）
//        candidates.sort(Comparator.comparingInt(RegisteredHandler::getPriority));
//
//        if (!candidates.isEmpty()) {
//            invokeMethod(candidates.get(0), message); // 调用优先级最高的
//            return;
//        }
//
//        // ===== 第二阶段：尝试匹配 @AtTextImageReplyMessage =====
//        for (Map.Entry<Method, RegisteredHandler> entry : messageHandlers.entrySet()) {
//            RegisteredHandler register = entry.getValue();
//            List<Annotation> annotations = entry.getValue().getAnnotations();
//
//            if (annotations.isEmpty()) continue;
//
//            if (annotations.stream().noneMatch(a -> a instanceof DefaultMessage)) continue;
//
//            if (!AnnotationUtils.validateSegmentsForAtTextImageReply(message.getMessage())) continue;
//
//            matchedRegister = register;
//            break;
//        }
//
//        if (matchedRegister != null) {
//            invokeMethod(matchedRegister, message);
//        } else {
//            log.debug("没有找到匹配的消息处理器");
//        }
//    }