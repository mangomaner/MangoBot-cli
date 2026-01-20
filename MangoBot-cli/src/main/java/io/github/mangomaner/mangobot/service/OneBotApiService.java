package io.github.mangomaner.mangobot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import io.github.mangomaner.mangobot.manager.websocket.BotConnectionManager;
import io.github.mangomaner.mangobot.manager.websocket.EchoHandler;
import io.github.mangomaner.mangobot.model.onebot.SendMessage;
import io.github.mangomaner.mangobot.model.onebot.api.OneBotApiRequest;
import io.github.mangomaner.mangobot.model.onebot.api.OneBotApiResponse;
import io.github.mangomaner.mangobot.model.onebot.api.response.*;
import io.github.mangomaner.mangobot.model.onebot.event.message.GroupMessageEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * OneBot API 服务，用于发送消息和调用 API
 */
@Service
@Slf4j
public class OneBotApiService {

    private final BotConnectionManager connectionManager;
    private final ObjectMapper objectMapper;
    private final EchoHandler echoHandler;

    public OneBotApiService(BotConnectionManager connectionManager, ObjectMapper objectMapper, EchoHandler echoHandler) {
        this.connectionManager = connectionManager;
        this.objectMapper = objectMapper;
        this.echoHandler = echoHandler;
    }

    /**
     * 发送私聊消息
     *
     * @param botId  机器人QQ号
     * @param userId 对方QQ号
     * @param message 消息内容（支持 MessageBuilder 构建的 List<MessageSegment> 或 字符串）
     * @return MessageId
     */
    public MessageId sendPrivateMsg(long botId, long userId, SendMessage message) {
        Map<String, Object> params = new HashMap<>();
        params.put("user_id", userId);
        params.put("message", message.getMessage());

        return callApi(botId, "send_private_msg", params, MessageId.class);
    }

    /**
     * 发送群消息
     *
     * @param botId   机器人QQ号
     * @param groupId 群号
     * @param message 消息内容
     * @return MessageId
     */
    public MessageId sendGroupMsg(long botId, long groupId, SendMessage message) {
        Map<String, Object> params = new HashMap<>();
        params.put("group_id", groupId);
        params.put("message", message.getMessage());
        return callApi(botId, "send_group_msg", params, MessageId.class);
    }

    /**
     * 群组戳一戳
     *
     * @return
     */
    public MessageId sendGroupPoke(long botId, long groupId, long userId) {
        Map<String, Object> params = new HashMap<>();
        params.put("group_id", groupId);
        params.put("user_id", userId);
        return callApi(botId, "group_poke", params, MessageId.class);
    }

    /**
     * 私聊戳一戳
     *
     * @return
     */
    public MessageId sendFriendPoke(long botId, long userId) {
        Map<String, Object> params = new HashMap<>();
        params.put("user_id", userId);
        return callApi(botId, "friend_poke", params, MessageId.class);
    }

    /**
     * 撤回消息
     *
     * @param botId     机器人QQ号
     * @param messageId 消息ID
     */
    public void deleteMsg(long botId, int messageId) {
        Map<String, Object> params = new HashMap<>();
        params.put("message_id", messageId);
        callApiVoid(botId, "delete_msg", params);
    }

    /**
     * 发送合并转发消息 (群)
     *
     * @param botId 机器人QQ号
     * @param groupId 群号
     * @param messages 消息节点列表
     */
    public MessageId sendGroupForwardMsg(long botId, long groupId, Object messages) {
        Map<String, Object> params = new HashMap<>();
        params.put("group_id", groupId);
        params.put("messages", messages);
        return callApi(botId, "send_group_forward_msg", params, MessageId.class);
    }

    /**
     * 发送合并转发消息 (私聊)
     *
     * @param botId 机器人QQ号
     * @param userId 用户QQ号
     * @param messages 消息节点列表
     */
    public MessageId sendPrivateForwardMsg(long botId, long userId, Object messages) {
        Map<String, Object> params = new HashMap<>();
        params.put("user_id", userId);
        params.put("messages", messages);
        return callApi(botId, "send_private_forward_msg", params, MessageId.class);
    }

    /**
     * 获取群信息
     *
     * @param botId   机器人QQ号
     * @param groupId 群号
     * @param noCache 是否不使用缓存
     */
    public GroupInfo getGroupInfo(long botId, long groupId, boolean noCache) {
        Map<String, Object> params = new HashMap<>();
        params.put("group_id", groupId);
        params.put("no_cache", noCache);
        return callApi(botId, "get_group_info", params, GroupInfo.class);
    }

    /**
     * 发送消息 (通用)
     */
    @Deprecated
    public MessageId sendMsg(long botId, String messageType, Long userId, Long groupId, Object message) {
        Map<String, Object> params = new HashMap<>();
        params.put("message_type", messageType);
        if (userId != null) params.put("user_id", userId);
        if (groupId != null) params.put("group_id", groupId);
        params.put("message", message);
        return callApi(botId, "send_msg", params, MessageId.class);
    }

    /**
     * 获取消息
     */
    public MessageInfo getMsg(long botId, int messageId) {
        Map<String, Object> params = new HashMap<>();
        params.put("message_id", messageId);
        return callApi(botId, "get_msg", params, MessageInfo.class);
    }

    /**
     * 获取合并转发消息
     */
    public List<GroupMessageEvent> getForwardMsg(long botId, String id) {
        Map<String, Object> params = new HashMap<>();
        params.put("id", id);
        Map<String, Object> apiResult = callApi(botId, "get_forward_msg", params, Map.class);
        List<Map<String, Object>> result = (List<Map<String, Object>>) apiResult.get("messages");

        List<GroupMessageEvent> resultList = new ArrayList<>();
        for (Map<String, Object> item : result) {
            try {
                Map<String, Object> modifiedItem = new HashMap<>(item);
                if (modifiedItem.containsKey("content")) {
                    modifiedItem.put("message", modifiedItem.get("content"));
                }
                GroupMessageEvent event = objectMapper.convertValue(modifiedItem, GroupMessageEvent.class);
                resultList.add(event);
            } catch (Exception e) {
                log.error("Failed to parse forward message: {}", item, e);
            }
        }
        return resultList;
    }

    /**
     * 发送好友赞
     */
    public void sendLike(long botId, long userId, int times) {
        Map<String, Object> params = new HashMap<>();
        params.put("user_id", userId);
        params.put("times", times);
        callApiVoid(botId, "send_like", params);
    }

    /**
     * 群组踢人
     */
    public void setGroupKick(long botId, long groupId, long userId, boolean rejectAddRequest) {
        Map<String, Object> params = new HashMap<>();
        params.put("group_id", groupId);
        params.put("user_id", userId);
        params.put("reject_add_request", rejectAddRequest);
        callApiVoid(botId, "set_group_kick", params);
    }

    /**
     * 群组单人禁言
     */
    public void setGroupBan(long botId, long groupId, long userId, long duration) {
        Map<String, Object> params = new HashMap<>();
        params.put("group_id", groupId);
        params.put("user_id", userId);
        params.put("duration", duration);
        callApiVoid(botId, "set_group_ban", params);
    }

    /**
     * 群组全员禁言
     */
    public void setGroupWholeBan(long botId, long groupId, boolean enable) {
        Map<String, Object> params = new HashMap<>();
        params.put("group_id", groupId);
        params.put("enable", enable);
        callApiVoid(botId, "set_group_whole_ban", params);
    }

    /**
     * 群组设置管理员
     */
    public void setGroupAdmin(long botId, long groupId, long userId, boolean enable) {
        Map<String, Object> params = new HashMap<>();
        params.put("group_id", groupId);
        params.put("user_id", userId);
        params.put("enable", enable);
        callApiVoid(botId, "set_group_admin", params);
    }

    /**
     * 群组匿名
     */
    public void setGroupAnonymous(long botId, long groupId, boolean enable) {
        Map<String, Object> params = new HashMap<>();
        params.put("group_id", groupId);
        params.put("enable", enable);
        callApiVoid(botId, "set_group_anonymous", params);
    }

    /**
     * 设置群名片
     */
    public void setGroupCard(long botId, long groupId, long userId, String card) {
        Map<String, Object> params = new HashMap<>();
        params.put("group_id", groupId);
        params.put("user_id", userId);
        params.put("card", card);
        callApiVoid(botId, "set_group_card", params);
    }

    /**
     * 设置群名
     */
    public void setGroupName(long botId, long groupId, String groupName) {
        Map<String, Object> params = new HashMap<>();
        params.put("group_id", groupId);
        params.put("group_name", groupName);
        callApiVoid(botId, "set_group_name", params);
    }

    /**
     * 退出群组
     */
    public void setGroupLeave(long botId, long groupId, boolean isDismiss) {
        Map<String, Object> params = new HashMap<>();
        params.put("group_id", groupId);
        params.put("is_dismiss", isDismiss);
        callApiVoid(botId, "set_group_leave", params);
    }

    /**
     * 设置群组专属头衔
     */
    public void setGroupSpecialTitle(long botId, long groupId, long userId, String specialTitle, long duration) {
        Map<String, Object> params = new HashMap<>();
        params.put("group_id", groupId);
        params.put("user_id", userId);
        params.put("special_title", specialTitle);
        params.put("duration", duration);
        callApiVoid(botId, "set_group_special_title", params);
    }

    /**
     * 处理加好友请求
     */
    public void setFriendAddRequest(long botId, String flag, boolean approve, String remark) {
        Map<String, Object> params = new HashMap<>();
        params.put("flag", flag);
        params.put("approve", approve);
        params.put("remark", remark);
        callApiVoid(botId, "set_friend_add_request", params);
    }

    /**
     * 处理加群请求／邀请
     */
    public void setGroupAddRequest(long botId, String flag, String subType, boolean approve, String reason) {
        Map<String, Object> params = new HashMap<>();
        params.put("flag", flag);
        params.put("sub_type", subType);
        params.put("approve", approve);
        params.put("reason", reason);
        callApiVoid(botId, "set_group_add_request", params);
    }

    /**
     * 获取登录号信息
     */
    public LoginInfo getLoginInfo(long botId) {
        return callApi(botId, "get_login_info", new HashMap<>(), LoginInfo.class);
    }

    /**
     * 获取陌生人信息
     */
    public StrangerInfo getStrangerInfo(long botId, long userId, boolean noCache) {
        Map<String, Object> params = new HashMap<>();
        params.put("user_id", userId);
        params.put("no_cache", noCache);
        return callApi(botId, "get_stranger_info", params, StrangerInfo.class);
    }

    /**
     * 获取好友列表
     */
    public List<FriendInfo> getFriendList(long botId) {
        return callApiList(botId, "get_friend_list", new HashMap<>(), FriendInfo.class);
    }

    /**
     * 获取群列表
     */
    public List<GroupInfo> getGroupList(long botId) {
        return callApiList(botId, "get_group_list", new HashMap<>(), GroupInfo.class);
    }

    /**
     * 获取群成员信息
     */
    public GroupMemberInfo getGroupMemberInfo(long botId, long groupId, long userId, boolean noCache) {
        Map<String, Object> params = new HashMap<>();
        params.put("group_id", groupId);
        params.put("user_id", userId);
        params.put("no_cache", noCache);
        return callApi(botId, "get_group_member_info", params, GroupMemberInfo.class);
    }

    /**
     * 获取群成员列表
     */
    public List<GroupMemberInfo> getGroupMemberList(long botId, long groupId) {
        Map<String, Object> params = new HashMap<>();
        params.put("group_id", groupId);
        return callApiList(botId, "get_group_member_list", params, GroupMemberInfo.class);
    }

    /**
     * 获取群荣誉信息
     */
    public GroupHonorInfo getGroupHonorInfo(long botId, long groupId, String type) {
        Map<String, Object> params = new HashMap<>();
        params.put("group_id", groupId);
        params.put("type", type);
        return callApi(botId, "get_group_honor_info", params, GroupHonorInfo.class);
    }

    /**
     * 获取语音
     *   @param file 收到的语音文件名（消息段的 file 参数），如 0B38145AA44505000B38145AA4450500.silk
     *   @param outFormat 要转换到的格式，目前支持 mp3、amr、wma、m4a、spx、ogg、wav、flac
     *   @return FileInfo
     */
    public FileInfo getRecord(long botId, String file, String outFormat) {
        Map<String, Object> params = new HashMap<>();
        params.put("file", file);
        params.put("out_format", outFormat);
        return callApi(botId, "get_record", params, FileInfo.class);
    }

    /**
     * 获取图片
     *  @param file 收到的图片文件名（消息段的 file 参数），如 6B4DE3DFD1BD271E3297859D41C530F5.jpg
     *  @return FileInfo
     */
    public FileInfo getImage(long botId, String file) {
        Map<String, Object> params = new HashMap<>();
        params.put("file", file);
        return callApi(botId, "get_image", params, FileInfo.class);
    }

    /**
     * 检查是否可以发送图片
     */
    public CanSendInfo canSendImage(long botId) {
        return callApi(botId, "can_send_image", new HashMap<>(), CanSendInfo.class);
    }

    /**
     * 检查是否可以发送语音
     */
    public CanSendInfo canSendRecord(long botId) {
        return callApi(botId, "can_send_record", new HashMap<>(), CanSendInfo.class);
    }

    /**
     * 通用 API 调用方法 (返回 void)
     */
    public void callApiVoid(long botId, String action, Map<String, Object> params) {
        callApi(botId, action, params, Void.class);
    }

    /**
     * 通用 API 调用方法 (返回 List)
     */
    public <T> List<T> callApiList(long botId, String action, Map<String, Object> params, Class<T> elementType) {
        Object data = callApiRaw(botId, action, params);
        if (data == null) {
            return Collections.emptyList();
        }
        CollectionType listType = objectMapper.getTypeFactory().constructCollectionType(List.class, elementType);
        return objectMapper.convertValue(data, listType);
    }

    /**
     * 通用 API 调用方法 (返回指定类型)
     *
     * @param botId  机器人QQ号
     * @param action API 动作名称
     * @param params 参数
     * @param responseType 返回值类型
     * @return 响应数据对象
     */
    public <T> T callApi(long botId, String action, Map<String, Object> params, Class<T> responseType) {
        Object data = callApiRaw(botId, action, params);
        if (data == null) {
            return null;
        }
        return objectMapper.convertValue(data, responseType);
    }

    /**
     * 基础 API 调用 (返回原始 Object data)
     */
    private Object callApiRaw(long botId, String action, Map<String, Object> params) {
        BotConnectionManager.BotSession botSession = connectionManager.getSession(botId);
        if (botSession == null || !botSession.isConnected()) {
            log.error("机器人 {} 未连接或会话不存在，无法发送 API 请求: {}", botId, action);
            return null;
        }

        String echo = UUID.randomUUID().toString();
        OneBotApiRequest request = new OneBotApiRequest();
        request.setAction(action);
        request.setParams(params);
        request.setEcho(echo);

        try {
            // 注册等待
            echoHandler.register(echo);
            
            String json = objectMapper.writeValueAsString(request);
            log.debug("发送 API 请求 [{}]: {}", action, json);

            // 特别注意，WebSocket Session 不是线程安全的，对同一个 Session 的写操作都必须同步
            synchronized (botSession.getSession()) {
                botSession.getSession().sendMessage(new TextMessage(json));
            }
            
            // 同步等待响应，超时 60 秒
            OneBotApiResponse response = echoHandler.waitForResponse(echo, 60, TimeUnit.SECONDS);
            
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
