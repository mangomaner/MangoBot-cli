package org.mango.mangobot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.mango.mangobot.manager.websocket.BotConnectionManager;
import org.mango.mangobot.manager.websocket.EchoHandler;
import org.mango.mangobot.model.onebot.SendMessage;
import org.mango.mangobot.model.onebot.api.OneBotApiRequest;
import org.mango.mangobot.model.onebot.api.OneBotApiResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
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
     * @return 响应数据 (JSON String)
     */
    public String sendPrivateMsg(long botId, long userId, SendMessage message) {
        Map<String, Object> params = new HashMap<>();
        params.put("user_id", userId);
        params.put("message", message);
        return callApi(botId, "send_private_msg", params);
    }

    /**
     * 发送群消息
     *
     * @param botId   机器人QQ号
     * @param groupId 群号
     * @param message 消息内容
     * @return 响应数据 (JSON String)
     */
    public String sendGroupMsg(long botId, long groupId, SendMessage message) {
        Map<String, Object> params = new HashMap<>();
        params.put("group_id", groupId);
        params.put("message", message.getMessage());
        return callApi(botId, "send_group_msg", params);
    }

    /**
     * 撤回消息
     *
     * @param botId     机器人QQ号
     * @param messageId 消息ID
     */
    public String deleteMsg(long botId, int messageId) {
        Map<String, Object> params = new HashMap<>();
        params.put("message_id", messageId);
        return callApi(botId, "delete_msg", params);
    }

    /**
     * 发送合并转发消息 (群)
     *
     * @param botId 机器人QQ号
     * @param groupId 群号
     * @param messages 消息节点列表
     */
    public String sendGroupForwardMsg(long botId, long groupId, Object messages) {
        Map<String, Object> params = new HashMap<>();
        params.put("group_id", groupId);
        params.put("messages", messages);
        return callApi(botId, "send_group_forward_msg", params);
    }

    /**
     * 发送合并转发消息 (私聊)
     *
     * @param botId 机器人QQ号
     * @param userId 用户QQ号
     * @param messages 消息节点列表
     */
    public String sendPrivateForwardMsg(long botId, long userId, Object messages) {
        Map<String, Object> params = new HashMap<>();
        params.put("user_id", userId);
        params.put("messages", messages);
        return callApi(botId, "send_private_forward_msg", params);
    }

    /**
     * 获取群信息
     *
     * @param botId   机器人QQ号
     * @param groupId 群号
     * @param noCache 是否不使用缓存
     */
    public String getGroupInfo(long botId, long groupId, boolean noCache) {
        Map<String, Object> params = new HashMap<>();
        params.put("group_id", groupId);
        params.put("no_cache", noCache);
        return callApi(botId, "get_group_info", params);
    }

    /**
     * 发送消息 (通用)
     */
    @Deprecated
    public String sendMsg(long botId, String messageType, Long userId, Long groupId, Object message) {
        Map<String, Object> params = new HashMap<>();
        params.put("message_type", messageType);
        if (userId != null) params.put("user_id", userId);
        if (groupId != null) params.put("group_id", groupId);
        params.put("message", message);
        return callApi(botId, "send_msg", params);
    }

    /**
     * 获取消息
     */
    public String getMsg(long botId, int messageId) {
        Map<String, Object> params = new HashMap<>();
        params.put("message_id", messageId);
        return callApi(botId, "get_msg", params);
    }

    /**
     * 获取合并转发消息
     */
    public String getForwardMsg(long botId, String id) {
        Map<String, Object> params = new HashMap<>();
        params.put("id", id);
        return callApi(botId, "get_forward_msg", params);
    }

    /**
     * 发送好友赞
     */
    public String sendLike(long botId, long userId, int times) {
        Map<String, Object> params = new HashMap<>();
        params.put("user_id", userId);
        params.put("times", times);
        return callApi(botId, "send_like", params);
    }

    /**
     * 群组踢人
     */
    public String setGroupKick(long botId, long groupId, long userId, boolean rejectAddRequest) {
        Map<String, Object> params = new HashMap<>();
        params.put("group_id", groupId);
        params.put("user_id", userId);
        params.put("reject_add_request", rejectAddRequest);
        return callApi(botId, "set_group_kick", params);
    }

    /**
     * 群组单人禁言
     */
    public String setGroupBan(long botId, long groupId, long userId, long duration) {
        Map<String, Object> params = new HashMap<>();
        params.put("group_id", groupId);
        params.put("user_id", userId);
        params.put("duration", duration);
        return callApi(botId, "set_group_ban", params);
    }

    /**
     * 群组全员禁言
     */
    public String setGroupWholeBan(long botId, long groupId, boolean enable) {
        Map<String, Object> params = new HashMap<>();
        params.put("group_id", groupId);
        params.put("enable", enable);
        return callApi(botId, "set_group_whole_ban", params);
    }

    /**
     * 群组设置管理员
     */
    public String setGroupAdmin(long botId, long groupId, long userId, boolean enable) {
        Map<String, Object> params = new HashMap<>();
        params.put("group_id", groupId);
        params.put("user_id", userId);
        params.put("enable", enable);
        return callApi(botId, "set_group_admin", params);
    }

    /**
     * 群组匿名
     */
    public String setGroupAnonymous(long botId, long groupId, boolean enable) {
        Map<String, Object> params = new HashMap<>();
        params.put("group_id", groupId);
        params.put("enable", enable);
        return callApi(botId, "set_group_anonymous", params);
    }

    /**
     * 设置群名片
     */
    public String setGroupCard(long botId, long groupId, long userId, String card) {
        Map<String, Object> params = new HashMap<>();
        params.put("group_id", groupId);
        params.put("user_id", userId);
        params.put("card", card);
        return callApi(botId, "set_group_card", params);
    }

    /**
     * 设置群名
     */
    public String setGroupName(long botId, long groupId, String groupName) {
        Map<String, Object> params = new HashMap<>();
        params.put("group_id", groupId);
        params.put("group_name", groupName);
        return callApi(botId, "set_group_name", params);
    }

    /**
     * 退出群组
     */
    public String setGroupLeave(long botId, long groupId, boolean isDismiss) {
        Map<String, Object> params = new HashMap<>();
        params.put("group_id", groupId);
        params.put("is_dismiss", isDismiss);
        return callApi(botId, "set_group_leave", params);
    }

    /**
     * 设置群组专属头衔
     */
    public String setGroupSpecialTitle(long botId, long groupId, long userId, String specialTitle, long duration) {
        Map<String, Object> params = new HashMap<>();
        params.put("group_id", groupId);
        params.put("user_id", userId);
        params.put("special_title", specialTitle);
        params.put("duration", duration);
        return callApi(botId, "set_group_special_title", params);
    }

    /**
     * 处理加好友请求
     */
    public String setFriendAddRequest(long botId, String flag, boolean approve, String remark) {
        Map<String, Object> params = new HashMap<>();
        params.put("flag", flag);
        params.put("approve", approve);
        params.put("remark", remark);
        return callApi(botId, "set_friend_add_request", params);
    }

    /**
     * 处理加群请求／邀请
     */
    public String setGroupAddRequest(long botId, String flag, String subType, boolean approve, String reason) {
        Map<String, Object> params = new HashMap<>();
        params.put("flag", flag);
        params.put("sub_type", subType);
        params.put("approve", approve);
        params.put("reason", reason);
        return callApi(botId, "set_group_add_request", params);
    }

    /**
     * 获取登录号信息
     */
    public String getLoginInfo(long botId) {
        return callApi(botId, "get_login_info", new HashMap<>());
    }

    /**
     * 获取陌生人信息
     */
    public String getStrangerInfo(long botId, long userId, boolean noCache) {
        Map<String, Object> params = new HashMap<>();
        params.put("user_id", userId);
        params.put("no_cache", noCache);
        return callApi(botId, "get_stranger_info", params);
    }

    /**
     * 获取好友列表
     */
    public String getFriendList(long botId) {
        return callApi(botId, "get_friend_list", new HashMap<>());
    }

    /**
     * 获取群列表
     */
    public String getGroupList(long botId) {
        return callApi(botId, "get_group_list", new HashMap<>());
    }

    /**
     * 获取群成员信息
     */
    public String getGroupMemberInfo(long botId, long groupId, long userId, boolean noCache) {
        Map<String, Object> params = new HashMap<>();
        params.put("group_id", groupId);
        params.put("user_id", userId);
        params.put("no_cache", noCache);
        return callApi(botId, "get_group_member_info", params);
    }

    /**
     * 获取群成员列表
     */
    public String getGroupMemberList(long botId, long groupId) {
        Map<String, Object> params = new HashMap<>();
        params.put("group_id", groupId);
        return callApi(botId, "get_group_member_list", params);
    }

    /**
     * 获取群荣誉信息
     */
    public String getGroupHonorInfo(long botId, long groupId, String type) {
        Map<String, Object> params = new HashMap<>();
        params.put("group_id", groupId);
        params.put("type", type);
        return callApi(botId, "get_group_honor_info", params);
    }

    /**
     * 获取语音
     *   @param file 收到的语音文件名（消息段的 file 参数），如 0B38145AA44505000B38145AA4450500.silk
     *   @param outFormat 要转换到的格式，目前支持 mp3、amr、wma、m4a、spx、ogg、wav、flac
     *   @return 转换后的语音文件路径，如 /home/somebody/cqhttp/data/record/0B38145AA44505000B38145AA4450500.mp3
     */
    public String getRecord(long botId, String file, String outFormat) {
        Map<String, Object> params = new HashMap<>();
        params.put("file", file);
        params.put("out_format", outFormat);
        return callApi(botId, "get_record", params);
    }

    /**
     * 获取图片
     *  @param file 收到的图片文件名（消息段的 file 参数），如 6B4DE3DFD1BD271E3297859D41C530F5.jpg
     *  @return 下载后的图片文件路径，如 /data/image/6B4DE3DFD1BD271E3297859D41C530F5.jpg
     */
    public String getImage(long botId, String file) {
        Map<String, Object> params = new HashMap<>();
        params.put("file", file);
        return callApi(botId, "get_image", params);
    }

    /**
     * 检查是否可以发送图片
     */
    public String canSendImage(long botId) {
        return callApi(botId, "can_send_image", new HashMap<>());
    }

    /**
     * 检查是否可以发送语音
     */
    public String canSendRecord(long botId) {
        return callApi(botId, "can_send_record", new HashMap<>());
    }

    /**
     * 通用 API 调用方法 (同步等待响应)
     *
     * @param botId  机器人QQ号
     * @param action API 动作名称
     * @param params 参数
     * @return 响应的 data 字段 (JSON String)
     */
    public String callApi(long botId, String action, Map<String, Object> params) {
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
            
            return objectMapper.writeValueAsString(response.getData());
            
        } catch (Exception e) {
            log.error("发送 API 请求失败: {}", action, e);
            return null;
        }
    }
}



///**
// * 获取 Cookies
// */
//public String getCookies(long botId, String domain) {
//    Map<String, Object> params = new HashMap<>();
//    params.put("domain", domain);
//    return callApi(botId, "get_cookies", params);
//}
//
///**
// * 获取 CSRF Token
// */
//public String getCsrfToken(long botId) {
//    return callApi(botId, "get_csrf_token", new HashMap<>());
//}
//
///**
// * 获取 QQ 相关接口凭证
// */
//public String getCredentials(long botId, String domain) {
//    Map<String, Object> params = new HashMap<>();
//    params.put("domain", domain);
//    return callApi(botId, "get_credentials", params);
//}