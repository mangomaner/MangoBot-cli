package io.github.mangomaner.mangobot.adapter.onebot.handler.outbound.send;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import io.github.mangomaner.mangobot.events.onebot.message.OneBotGroupMessageEvent;
import io.github.mangomaner.mangobot.events.onebot.message.OneBotPrivateMessageEvent;
import io.github.mangomaner.mangobot.adapter.onebot.model.event.message.OneBotMessageEvent;
import io.github.mangomaner.mangobot.adapter.onebot.handler.echo.OneBotApiResponse;
import io.github.mangomaner.mangobot.adapter.onebot.handler.echo.OneBotEchoHandler;
import io.github.mangomaner.mangobot.adapter.onebot.model.dto.OneBotApiRequest;
import io.github.mangomaner.mangobot.adapter.onebot.handler.outbound.build_sending_message.OneBotSendingMessage;
import io.github.mangomaner.mangobot.adapter.onebot.model.vo.*;
import io.github.mangomaner.mangobot.adapter.onebot.handler.outbound.build_sending_message.LocationCard;
import io.github.mangomaner.mangobot.adapter.onebot.utils.OneBotMessageParser;
import io.github.mangomaner.mangobot.infra.websocket.ConnectionSessionManager;
import io.github.mangomaner.mangobot.infra.websocket.model.ConnectionSession;
import io.github.mangomaner.mangobot.module.message.groupMessage.service.GroupMessagesService;
import io.github.mangomaner.mangobot.module.message.model.domain.GroupMessages;
import io.github.mangomaner.mangobot.module.message.model.domain.PrivateMessages;
import io.github.mangomaner.mangobot.module.message.privateMessage.service.PrivateMessagesService;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.WebSocket;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class OneBotApiService {

    private final ConnectionSessionManager sessionManager;
    private final ObjectMapper objectMapper;
    private final OneBotEchoHandler oneBotEchoHandler;
    private final GroupMessagesService groupMessagesService;
    private final PrivateMessagesService privateMessagesService;
    private final OneBotMessageParser messageParser;

    public OneBotApiService(
            ConnectionSessionManager sessionManager,
            ObjectMapper objectMapper,
            OneBotEchoHandler oneBotEchoHandler,
            GroupMessagesService groupMessagesService,
            PrivateMessagesService privateMessagesService,
            OneBotMessageParser messageParser) {
        this.sessionManager = sessionManager;
        this.objectMapper = objectMapper;
        this.oneBotEchoHandler = oneBotEchoHandler;
        this.groupMessagesService = groupMessagesService;
        this.privateMessagesService = privateMessagesService;
        this.messageParser = messageParser;
    }

    public MessageId sendPrivateMsg(long botId, long userId, OneBotSendingMessage message) {
        Map<String, Object> params = new HashMap<>();
        params.put("user_id", userId);
        params.put("message", message.getMessage());

        MessageId result = callApi(botId, "send_private_msg", params, MessageId.class);
        
        savePrivateMessage(botId, userId, message, result);
        
        return result;
    }

    public MessageId sendGroupMsg(long botId, long groupId, OneBotSendingMessage message) {
        Map<String, Object> params = new HashMap<>();
        params.put("group_id", groupId);
        params.put("message", message.getMessage());
        
        MessageId result = callApi(botId, "send_group_msg", params, MessageId.class);
        
        saveGroupMessage(botId, groupId, message, result);
        
        return result;
    }

    private void savePrivateMessage(long botId, long userId, OneBotSendingMessage message, MessageId result) {
        if (result == null || privateMessagesService == null || messageParser == null) {
            return;
        }
        try {
            PrivateMessages privateMessages = new PrivateMessages();
            privateMessages.setBotId(String.valueOf(botId));
            privateMessages.setFriendId(String.valueOf(userId));
            privateMessages.setMessageId(String.valueOf(result.getMessageId()));
            privateMessages.setSenderId(String.valueOf(botId));
            privateMessages.setMessageSegments(objectMapper.writeValueAsString(message.getMessage()));
            privateMessages.setMessageTime(System.currentTimeMillis());
            privateMessages.setParseMessage(messageParser.parseMessage(message.getMessage(), botId));
            privateMessagesService.save(privateMessages);
        } catch (Exception e) {
            log.error("Failed to save private message", e);
        }
    }

    private void saveGroupMessage(long botId, long groupId, OneBotSendingMessage message, MessageId result) {
        if (result == null || groupMessagesService == null || messageParser == null) {
            return;
        }
        try {
            GroupMessages groupMessages = new GroupMessages();
            groupMessages.setBotId(String.valueOf(botId));
            groupMessages.setGroupId(String.valueOf(groupId));
            groupMessages.setMessageId(String.valueOf(result.getMessageId()));
            groupMessages.setSenderId(String.valueOf(botId));
            groupMessages.setMessageSegments(objectMapper.writeValueAsString(message.getMessage()));
            groupMessages.setMessageTime(System.currentTimeMillis());
            groupMessages.setParseMessage(messageParser.parseMessage(message.getMessage(), botId));
            groupMessagesService.addGroupMessage(groupMessages);
        } catch (Exception e) {
            log.error("Failed to save group message", e);
        }
    }

    public Void sendGroupPoke(long botId, long groupId, long userId) {
        Map<String, Object> params = new HashMap<>();
        params.put("group_id", groupId);
        params.put("user_id", userId);
        return callApi(botId, "group_poke", params, Void.class);
    }

    /**
     * 发一张位置卡片到群里。
     *
     * <p>走的是腾讯地图 Ark（见 {@link LocationCard}），不是 OneBot 的 {@code location} 段——
     * 后者在当前 NapCat/QQ 上发出去客户端不认。
     *
     * @param lat     纬度
     * @param lng     经度
     * @param name    地点名；空则用 {@code 我在这}
     * @param address 详细地址；可空
     */
    public MessageId sendGroupLocation(long botId, long groupId, String lat, String lng,
                                       String name, String address) {
        OneBotSendingMessage message = locationMessage(botId, groupId, lat, lng, name, address);
        return sendGroupMsg(botId, groupId, message);
    }

    /**
     * 发一张位置卡片到私聊。参数语义与 {@link #sendGroupLocation} 一致。
     *
     * @param userId 好友 QQ 号
     */
    public MessageId sendPrivateLocation(long botId, long userId, String lat, String lng,
                                         String name, String address) {
        OneBotSendingMessage message = locationMessage(botId, userId, lat, lng, name, address);
        return sendPrivateMsg(botId, userId, message);
    }

    /**
     * 位置卡片自己一张就是一条消息——Ark 卡片和文本挤在一条里 QQ 渲染会出问题，
     * 所以这里刻意只放这一段，不提供"再加句话"的重载。
     */
    private OneBotSendingMessage locationMessage(long botId, long peerId, String lat, String lng,
                                                 String name, String address) {
        OneBotSendingMessage message = new OneBotSendingMessage();
        message.setMessage(List.of(LocationCard.toSegment(botId, peerId, lat, lng, name, address)));
        return message;
    }

    public Void sendPrivatePoke(long botId, long userId) {
        Map<String, Object> params = new HashMap<>();
        params.put("user_id", userId);
        return callApi(botId, "friend_poke", params, Void.class);
    }

    public void deleteMsg(long botId, int messageId) {
        Map<String, Object> params = new HashMap<>();
        params.put("message_id", messageId);
        callApiVoid(botId, "delete_msg", params);
    }

    public MessageId sendGroupForwardMsg(long botId, long groupId, Object messages) {
        Map<String, Object> params = new HashMap<>();
        params.put("group_id", groupId);
        params.put("messages", messages);
        return callApi(botId, "send_group_forward_msg", params, MessageId.class);
    }

    public MessageId sendPrivateForwardMsg(long botId, long userId, Object messages) {
        Map<String, Object> params = new HashMap<>();
        params.put("user_id", userId);
        params.put("messages", messages);
        return callApi(botId, "send_private_forward_msg", params, MessageId.class);
    }

    public GroupInfo getGroupInfo(long botId, long groupId, boolean noCache) {
        Map<String, Object> params = new HashMap<>();
        params.put("group_id", groupId);
        params.put("no_cache", noCache);
        return callApi(botId, "get_group_info", params, GroupInfo.class);
    }

    @Deprecated
    public MessageId sendMsg(long botId, String messageType, Long userId, Long groupId, Object message) {
        Map<String, Object> params = new HashMap<>();
        params.put("message_type", messageType);
        if (userId != null) params.put("user_id", userId);
        if (groupId != null) params.put("group_id", groupId);
        params.put("message", message);
        return callApi(botId, "send_msg", params, MessageId.class);
    }

    public MessageInfo getMsg(long botId, int messageId) {
        Map<String, Object> params = new HashMap<>();
        params.put("message_id", messageId);
        return callApi(botId, "get_msg", params, MessageInfo.class);
    }

    public List<OneBotGroupMessageEvent> getForwardMsg(long botId, String id) {
        Map<String, Object> params = new HashMap<>();
        params.put("id", id);
        Map<String, Object> apiResult = callApi(botId, "get_forward_msg", params, Map.class);
        return toMessageEvents(apiResult == null ? null : apiResult.get("messages"),
                OneBotGroupMessageEvent.class);
    }

    /**
     * 获取群历史消息。
     *
     * <p>只发 {@code group_id} / {@code message_seq} / {@code count} / {@code reverse_order} 四个参数，
     * 照 NapCat 文档的示例来；{@code disable_get_url} / {@code parse_mult_msg} / {@code quick_reply}
     * 交给服务端默认值，旧版的驼峰 {@code reverseOrder} 也不发（那是给老版本兜底的，这里不需要）。
     *
     * <p><b>方向怪癖（社区反馈，非官方文档）：</b>不传 {@code message_seq} 时是从最新消息往回取；
     * 一旦传了 {@code message_seq}，实际行为是<b>从该条往后</b>取，而不是往回翻页。{@code reverse_order}
     * 只是把返回的列表翻转，不改变查询方向。所以想往前翻历史，目前只能靠"不传 message_seq + 加大 count"。
     *
     * @param messageSeq 起始消息序号；传 null 表示从最新往回取
     * @param count      要取多少条
     * @param reverseOrder 是否把结果列表反向排序
     * @return 解析失败或连接不可用时返回空列表，不返回 null
     */
    public List<OneBotGroupMessageEvent> getGroupMsgHistory(long botId, long groupId,
                                                            Integer messageSeq, int count, boolean reverseOrder) {
        Map<String, Object> params = new HashMap<>();
        params.put("group_id", groupId);
        if (messageSeq != null) {
            params.put("message_seq", messageSeq);
        }
        params.put("count", count);
        params.put("reverse_order", reverseOrder);

        Map<String, Object> apiResult = callApi(botId, "get_group_msg_history", params, Map.class);
        return toMessageEvents(apiResult == null ? null : apiResult.get("messages"),
                OneBotGroupMessageEvent.class);
    }

    /**
     * 获取好友历史消息。参数语义与 {@link #getGroupMsgHistory} 完全一致，只把群号换成好友号，
     * 包括那条 {@code message_seq} 方向怪癖。
     *
     * @param userId 好友 QQ 号
     * @return 解析失败或连接不可用时返回空列表，不返回 null
     */
    public List<OneBotPrivateMessageEvent> getFriendMsgHistory(long botId, long userId,
                                                               Integer messageSeq, int count, boolean reverseOrder) {
        Map<String, Object> params = new HashMap<>();
        params.put("user_id", userId);
        if (messageSeq != null) {
            params.put("message_seq", messageSeq);
        }
        params.put("count", count);
        params.put("reverse_order", reverseOrder);

        Map<String, Object> apiResult = callApi(botId, "get_friend_msg_history", params, Map.class);
        return toMessageEvents(apiResult == null ? null : apiResult.get("messages"),
                OneBotPrivateMessageEvent.class);
    }

    /**
     * 把 API 返回的 {@code data.messages} 逐条转成对应的事件对象。
     *
     * <p>三个调用方（合并转发 / 群历史 / 好友历史）共用：它们拿到的都是 OB11Message 数组，差别只在目标类型。
     *
     * <p>{@code rawMessages} 为 null 或不是数组时返回空列表——<b>这是刻意的</b>：机器人没连上、
     * 业务返回 retcode≠0、或者返回体里压根没有 {@code messages}，都会走到这里，都不该是 NPE。
     *
     * <p>{@code content} → {@code message} 的重映射保留自原来的 {@code getForwardMsg}：合并转发的条目
     * 用的字段名是 {@code content}，而历史消息用的是 {@code message}。对后者这个重映射不生效
     * （没有 content 键），所以两种来源都能喂进来。
     *
     * <p>单条转换失败只记日志跳过，不影响整批——这是原有的容错行为，没有改。
     */
    private <T extends OneBotMessageEvent> List<T> toMessageEvents(Object rawMessages, Class<T> eventType) {
        if (!(rawMessages instanceof List<?> items)) {
            return Collections.emptyList();
        }
        List<T> resultList = new ArrayList<>();
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> itemMap)) {
                continue;
            }
            try {
                Map<String, Object> modifiedItem = new HashMap<>();
                itemMap.forEach((key, value) -> modifiedItem.put(String.valueOf(key), value));
                if (modifiedItem.containsKey("content")) {
                    modifiedItem.put("message", modifiedItem.get("content"));
                }
                resultList.add(objectMapper.convertValue(modifiedItem, eventType));
            } catch (Exception e) {
                log.error("Failed to parse message into {}: {}", eventType.getSimpleName(), item, e);
            }
        }
        return resultList;
    }

    public void sendLike(long botId, long userId, int times) {
        Map<String, Object> params = new HashMap<>();
        params.put("user_id", userId);
        params.put("times", times);
        callApiVoid(botId, "send_like", params);
    }

    public void setGroupKick(long botId, long groupId, long userId, boolean rejectAddRequest) {
        Map<String, Object> params = new HashMap<>();
        params.put("group_id", groupId);
        params.put("user_id", userId);
        params.put("reject_add_request", rejectAddRequest);
        callApiVoid(botId, "set_group_kick", params);
    }

    public void setGroupBan(long botId, long groupId, long userId, long duration) {
        Map<String, Object> params = new HashMap<>();
        params.put("group_id", groupId);
        params.put("user_id", userId);
        params.put("duration", duration);
        callApiVoid(botId, "set_group_ban", params);
    }

    public void setGroupWholeBan(long botId, long groupId, boolean enable) {
        Map<String, Object> params = new HashMap<>();
        params.put("group_id", groupId);
        params.put("enable", enable);
        callApiVoid(botId, "set_group_whole_ban", params);
    }

    public void setGroupAdmin(long botId, long groupId, long userId, boolean enable) {
        Map<String, Object> params = new HashMap<>();
        params.put("group_id", groupId);
        params.put("user_id", userId);
        params.put("enable", enable);
        callApiVoid(botId, "set_group_admin", params);
    }

    public void setGroupAnonymous(long botId, long groupId, boolean enable) {
        Map<String, Object> params = new HashMap<>();
        params.put("group_id", groupId);
        params.put("enable", enable);
        callApiVoid(botId, "set_group_anonymous", params);
    }

    public void setGroupCard(long botId, long groupId, long userId, String card) {
        Map<String, Object> params = new HashMap<>();
        params.put("group_id", groupId);
        params.put("user_id", userId);
        params.put("card", card);
        callApiVoid(botId, "set_group_card", params);
    }

    public void setGroupName(long botId, long groupId, String groupName) {
        Map<String, Object> params = new HashMap<>();
        params.put("group_id", groupId);
        params.put("group_name", groupName);
        callApiVoid(botId, "set_group_name", params);
    }

    public void setGroupLeave(long botId, long groupId, boolean isDismiss) {
        Map<String, Object> params = new HashMap<>();
        params.put("group_id", groupId);
        params.put("is_dismiss", isDismiss);
        callApiVoid(botId, "set_group_leave", params);
    }

    public void setGroupSpecialTitle(long botId, long groupId, long userId, String specialTitle, long duration) {
        Map<String, Object> params = new HashMap<>();
        params.put("group_id", groupId);
        params.put("user_id", userId);
        params.put("special_title", specialTitle);
        params.put("duration", duration);
        callApiVoid(botId, "set_group_special_title", params);
    }

    public void setFriendAddRequest(long botId, String flag, boolean approve, String remark) {
        Map<String, Object> params = new HashMap<>();
        params.put("flag", flag);
        params.put("approve", approve);
        params.put("remark", remark);
        callApiVoid(botId, "set_friend_add_request", params);
    }

    public void setGroupAddRequest(long botId, String flag, String subType, boolean approve, String reason) {
        Map<String, Object> params = new HashMap<>();
        params.put("flag", flag);
        params.put("sub_type", subType);
        params.put("approve", approve);
        params.put("reason", reason);
        callApiVoid(botId, "set_group_add_request", params);
    }

    public LoginInfo getLoginInfo(long botId) {
        return callApi(botId, "get_login_info", new HashMap<>(), LoginInfo.class);
    }

    public StrangerInfo getStrangerInfo(long botId, long userId, boolean noCache) {
        Map<String, Object> params = new HashMap<>();
        params.put("user_id", userId);
        params.put("no_cache", noCache);
        return callApi(botId, "get_stranger_info", params, StrangerInfo.class);
    }

    public List<FriendInfo> getFriendList(long botId) {
        return callApiList(botId, "get_friend_list", new HashMap<>(), FriendInfo.class);
    }

    public List<GroupInfo> getGroupList(long botId) {
        return callApiList(botId, "get_group_list", new HashMap<>(), GroupInfo.class);
    }

    public GroupMemberInfo getGroupMemberInfo(long botId, long groupId, long userId, boolean noCache) {
        Map<String, Object> params = new HashMap<>();
        params.put("group_id", groupId);
        params.put("user_id", userId);
        params.put("no_cache", noCache);
        return callApi(botId, "get_group_member_info", params, GroupMemberInfo.class);
    }

    public List<GroupMemberInfo> getGroupMemberList(long botId, long groupId) {
        Map<String, Object> params = new HashMap<>();
        params.put("group_id", groupId);
        return callApiList(botId, "get_group_member_list", params, GroupMemberInfo.class);
    }

    public GroupHonorInfo getGroupHonorInfo(long botId, long groupId, String type) {
        Map<String, Object> params = new HashMap<>();
        params.put("group_id", groupId);
        params.put("type", type);
        return callApi(botId, "get_group_honor_info", params, GroupHonorInfo.class);
    }

    public FileInfo getRecord(long botId, String file, String outFormat) {
        Map<String, Object> params = new HashMap<>();
        params.put("file", file);
        params.put("out_format", outFormat);
        return callApi(botId, "get_record", params, FileInfo.class);
    }

    public FileInfo getImage(long botId, String file) {
        Map<String, Object> params = new HashMap<>();
        params.put("file", file);
        return callApi(botId, "get_image", params, FileInfo.class);
    }

    public CanSendInfo canSendImage(long botId) {
        return callApi(botId, "can_send_image", new HashMap<>(), CanSendInfo.class);
    }

    public CanSendInfo canSendRecord(long botId) {
        return callApi(botId, "can_send_record", new HashMap<>(), CanSendInfo.class);
    }

    public void callApiVoid(long botId, String action, Map<String, Object> params) {
        callApi(botId, action, params, Void.class);
    }

    public <T> List<T> callApiList(long botId, String action, Map<String, Object> params, Class<T> elementType) {
        Object data = callApiRaw(botId, action, params);
        if (data == null) {
            return Collections.emptyList();
        }
        CollectionType listType = objectMapper.getTypeFactory().constructCollectionType(List.class, elementType);
        return objectMapper.convertValue(data, listType);
    }

    public <T> T callApi(long botId, String action, Map<String, Object> params, Class<T> responseType) {
        Object data = callApiRaw(botId, action, params);
        if (data == null) {
            return null;
        }
        return objectMapper.convertValue(data, responseType);
    }

    private Object callApiRaw(long botId, String action, Map<String, Object> params) {
        ConnectionSession session = sessionManager.getSessionBySelfId(botId);
        if (session == null || !session.isConnected()) {
            log.error("机器人 {} 未连接或会话不存在，无法发送 API 请求: {}", botId, action);
            return null;
        }

        String echo = UUID.randomUUID().toString();
        OneBotApiRequest request = new OneBotApiRequest();
        request.setAction(action);
        request.setParams(params);
        request.setEcho(echo);

        try {
            oneBotEchoHandler.register(echo);
            
            String json = objectMapper.writeValueAsString(request);
            log.debug("发送 API 请求 [{}]: {}", action, json);

            WebSocket connection = session.getConnection();
            synchronized (connection) {
                connection.send(json);
            }
            
            OneBotApiResponse response = oneBotEchoHandler.waitForResponse(echo, 60, TimeUnit.SECONDS);
            
            if (response.getRetcode() != 0) {
                log.warn("API 调用返回非零状态: {} - {}", response.getRetcode(), response.getMessage());
            }
            
            return response.getData();
            
        } catch (Exception e) {
            log.error("发送 API 请求失败: {}", action, e);
            return null;
        }
    }
}
