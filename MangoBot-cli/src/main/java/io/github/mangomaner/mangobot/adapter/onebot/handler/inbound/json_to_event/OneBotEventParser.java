package io.github.mangomaner.mangobot.adapter.onebot.handler.inbound.json_to_event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mangomaner.mangobot.adapter.onebot.model.event.OneBotEvent;
import io.github.mangomaner.mangobot.events.onebot.message.OneBotGroupMessageEvent;
import io.github.mangomaner.mangobot.events.onebot.message.OneBotMessageSentEvent;
import io.github.mangomaner.mangobot.events.onebot.message.OneBotPrivateMessageEvent;
import io.github.mangomaner.mangobot.events.onebot.meta.OneBotHeartbeatEvent;
import io.github.mangomaner.mangobot.events.onebot.meta.OneBotLifecycleEvent;
import io.github.mangomaner.mangobot.events.onebot.notice.*;

/**
 * 入站 JSON → 具体事件对象的唯一入口：按 {@code post_type}（外加 {@code message_type} /
 * {@code meta_event_type} / {@code notice_type}）路由到对应的事件类。
 *
 * <p><b>路由逻辑为什么在这里，而不在 model 上挂 {@code @JsonDeserialize}</b>：
 * 以前 {@code OneBotBaseEvent} 上写着 {@code @JsonDeserialize(using = OneBotEventDeserializer.class)}，
 * 于是 model 包反过来 import 了 handler 包——全树唯一一处 model → handler 的逆依赖。而它连线到的是
 * {@code events/onebot/} 下那批**具体事件类**（它们又继承 model 里的事件基类），所以那条依赖还避不开。
 *
 * <p>挪到这边之后方向就正了：本类同时认识 model（基类）和 events（具体类），它做路由是本职；
 * model 不再认识任何人。代价是那批具体事件类上为了抵消基类注解而写的
 * {@code @JsonDeserialize(using = JsonDeserializer.None.class)} 全部删除——它们本来就是纯噪音。
 */
public class OneBotEventParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static OneBotEvent parse(String json) throws JsonProcessingException {
        if (json == null || json.isBlank()) {
            return null;
        }
        return parse(MAPPER.readTree(json));
    }

    /**
     * 按 {@code post_type} 分流。认不出的类型返回 {@code null}（而不是抛异常）——
     * 上游 {@code OneBotMessageHandler} 拿到的 null 会被忽略，一条陌生事件不该打断整条连接。
     */
    public static OneBotEvent parse(JsonNode node) throws JsonProcessingException {
        if (node == null) {
            return null;
        }
        Class<? extends OneBotEvent> targetClass = route(node);
        return targetClass == null ? null : MAPPER.treeToValue(node, targetClass);
    }

    private static Class<? extends OneBotEvent> route(JsonNode node) {
        String postType = text(node, "post_type");
        if (postType == null) {
            return null;
        }
        return switch (postType) {
            case "message" -> switch (text(node, "message_type")) {
                case "group" -> OneBotGroupMessageEvent.class;
                case "private" -> OneBotPrivateMessageEvent.class;
                default -> null;
            };
            case "message_sent" -> OneBotMessageSentEvent.class;
            case "meta_event" -> switch (text(node, "meta_event_type")) {
                case "heartbeat" -> OneBotHeartbeatEvent.class;
                case "lifecycle" -> OneBotLifecycleEvent.class;
                default -> null;
            };
            case "notice" -> switch (text(node, "notice_type")) {
                case "notify" -> PokeEvent.class;
                case "group_decrease" -> OneBotGroupDecreaseEvent.class;
                case "group_increase" -> OneBotGroupIncreaseEvent.class;
                case "group_ban" -> OneBotGroupBanEvent.class;
                case "essence" -> OneBotEssenceEvent.class;
                case "group_recall" -> OneBotGroupRecallEvent.class;
                case "group_upload" -> OneBotGroupUploadEvent.class;
                case "group_msg_emoji_like" -> OneBotGroupMsgEmojiLikeEvent.class;
                default -> null;
            };
            default -> null;
        };
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
