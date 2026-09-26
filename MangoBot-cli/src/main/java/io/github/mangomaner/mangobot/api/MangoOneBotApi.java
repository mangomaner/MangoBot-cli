package io.github.mangomaner.mangobot.api;

import io.github.mangomaner.mangobot.adapter.onebot.handler.outbound.build_sending_message.OneBotSendingMessage;
import io.github.mangomaner.mangobot.events.onebot.message.OneBotGroupMessageEvent;
import io.github.mangomaner.mangobot.events.onebot.message.OneBotPrivateMessageEvent;
import io.github.mangomaner.mangobot.adapter.onebot.handler.outbound.send.OneBotApiService;
import io.github.mangomaner.mangobot.adapter.onebot.model.vo.*;

import java.util.List;
import java.util.Map;

/**
 * OneBot API (静态工具类)
 * 提供对 OneBot 协议的 API 调用能力，包括发送消息、群管理、获取信息等。
 */
public class MangoOneBotApi {

    private static OneBotApiService service;

    private MangoOneBotApi() {}

    static void setService(OneBotApiService service) {
        MangoOneBotApi.service = service;
    }

    private static void checkService() {
        if (service == null) {
            throw new IllegalStateException("MangoOneBotApi has not been initialized yet.");
        }
    }

    /**
     * 发送私聊消息
     *
     * @param botId  机器人QQ号
     * @param userId 对方QQ号
     * @param message 消息内容
     * @return MessageId
     */
    public static MessageId sendPrivateMsg(long botId, long userId, OneBotSendingMessage message) {
        checkService();
        return service.sendPrivateMsg(botId, userId, message);
    }

    /**
     * 发送群消息
     *
     * @param botId   机器人QQ号
     * @param groupId 群号
     * @param message 消息内容
     * @return MessageId
     */
    public static MessageId sendGroupMsg(long botId, long groupId, OneBotSendingMessage message) {
        checkService();
        return service.sendGroupMsg(botId, groupId, message);
    }

    /**
     * 群组戳一戳
     */
    public static Void sendGroupPoke(long botId, long groupId, long userId) {
        checkService();
        return service.sendGroupPoke(botId, groupId, userId);
    }

    /**
     * 私聊戳一戳
     */
    public static Void sendPrivatePoke(long botId, long userId) {
        checkService();
        return service.sendPrivatePoke(botId, userId);
    }

    /**
     * 发送位置 (群聊)。走腾讯地图 Ark 卡片，不是 OneBot 的 {@code location} 段——
     * 后者在当前 NapCat/QQ 上发出去客户端显示「该消息类型暂不支持查看」。
     *
     * @param botId   机器人QQ号
     * @param groupId 群号
     * @param lat     纬度
     * @param lng     经度
     * @param name    地点名；空则显示「我在这」
     * @param address 详细地址；可空
     */
    public static MessageId sendGroupLocation(long botId, long groupId, String lat, String lng,
                                              String name, String address) {
        checkService();
        return service.sendGroupLocation(botId, groupId, lat, lng, name, address);
    }

    /**
     * 发送位置 (私聊)。参数语义与 {@link #sendGroupLocation} 一致。
     *
     * @param userId 好友QQ号
     */
    public static MessageId sendPrivateLocation(long botId, long userId, String lat, String lng,
                                                String name, String address) {
        checkService();
        return service.sendPrivateLocation(botId, userId, lat, lng, name, address);
    }

    /**
     * 撤回消息
     *
     * @param botId     机器人QQ号
     * @param messageId 消息ID
     */
    public static void deleteMsg(long botId, int messageId) {
        checkService();
        service.deleteMsg(botId, messageId);
    }

    /**
     * 发送合并转发消息 (群)
     *
     * @param botId 机器人QQ号
     * @param groupId 群号
     * @param messages 消息节点列表
     */
    public static MessageId sendGroupForwardMsg(long botId, long groupId, Object messages) {
        checkService();
        return service.sendGroupForwardMsg(botId, groupId, messages);
    }

    /**
     * 发送合并转发消息 (私聊)
     *
     * @param botId 机器人QQ号
     * @param userId 用户QQ号
     * @param messages 消息节点列表
     */
    public static MessageId sendPrivateForwardMsg(long botId, long userId, Object messages) {
        checkService();
        return service.sendPrivateForwardMsg(botId, userId, messages);
    }

    /**
     * 获取群信息
     *
     * @param botId   机器人QQ号
     * @param groupId 群号
     * @param noCache 是否不使用缓存
     */
    public static GroupInfo getGroupInfo(long botId, long groupId, boolean noCache) {
        checkService();
        return service.getGroupInfo(botId, groupId, noCache);
    }

    /**
     * 发送消息 (通用)
     */
    @Deprecated
    public static MessageId sendMsg(long botId, String messageType, Long userId, Long groupId, Object message) {
        checkService();
        return service.sendMsg(botId, messageType, userId, groupId, message);
    }

    /**
     * 获取消息
     */
    public static MessageInfo getMsg(long botId, int messageId) {
        checkService();
        return service.getMsg(botId, messageId);
    }

    /**
     * 获取合并转发消息
     */
    public static List<OneBotGroupMessageEvent> getForwardMsg(long botId, String id) {
        checkService();
        return service.getForwardMsg(botId, id);
    }

    /**
     * 获取群历史消息 (从最新往回取 count 条)
     *
     * @param botId   机器人QQ号
     * @param groupId 群号
     * @param count   要取多少条
     */
    public static List<OneBotGroupMessageEvent> getGroupMsgHistory(long botId, long groupId, int count) {
        return getGroupMsgHistory(botId, groupId, null, count, false);
    }

    /**
     * 获取群历史消息 (完整参数)
     *
     * <p><b>注意 message_seq 的方向怪癖</b>：传 null 是从最新往回取；一旦传了具体序号，NapCat 实际是
     * <b>从该条往后</b>取而不是往回翻页，{@code reverseOrder} 只翻转返回列表、不改变查询方向
     * （社区反馈，非官方文档）。想往前翻历史，目前只能"不传 messageSeq + 加大 count"。
     *
     * @param messageSeq   起始消息序号；null 表示从最新往回取
     * @param count        要取多少条
     * @param reverseOrder 是否把结果列表反向排序
     */
    public static List<OneBotGroupMessageEvent> getGroupMsgHistory(long botId, long groupId,
                                                                  Integer messageSeq, int count, boolean reverseOrder) {
        checkService();
        return service.getGroupMsgHistory(botId, groupId, messageSeq, count, reverseOrder);
    }

    /**
     * 获取私聊历史消息 (从最新往回取 count 条)
     *
     * @param botId  机器人QQ号
     * @param userId 好友QQ号
     * @param count  要取多少条
     */
    public static List<OneBotPrivateMessageEvent> getFriendMsgHistory(long botId, long userId, int count) {
        return getFriendMsgHistory(botId, userId, null, count, false);
    }

    /**
     * 获取私聊历史消息 (完整参数)
     *
     * <p>{@code messageSeq} 的方向怪癖与 {@link #getGroupMsgHistory} 完全一致。
     */
    public static List<OneBotPrivateMessageEvent> getFriendMsgHistory(long botId, long userId,
                                                                     Integer messageSeq, int count, boolean reverseOrder) {
        checkService();
        return service.getFriendMsgHistory(botId, userId, messageSeq, count, reverseOrder);
    }

    /**
     * 发送好友赞
     */
    public static void sendLike(long botId, long userId, int times) {
        checkService();
        service.sendLike(botId, userId, times);
    }

    /**
     * 群组踢人
     */
    public static void setGroupKick(long botId, long groupId, long userId, boolean rejectAddRequest) {
        checkService();
        service.setGroupKick(botId, groupId, userId, rejectAddRequest);
    }

    /**
     * 群组单人禁言
     */
    public static void setGroupBan(long botId, long groupId, long userId, long duration) {
        checkService();
        service.setGroupBan(botId, groupId, userId, duration);
    }

    /**
     * 群组全员禁言
     */
    public static void setGroupWholeBan(long botId, long groupId, boolean enable) {
        checkService();
        service.setGroupWholeBan(botId, groupId, enable);
    }

    /**
     * 群组设置管理员
     */
    public static void setGroupAdmin(long botId, long groupId, long userId, boolean enable) {
        checkService();
        service.setGroupAdmin(botId, groupId, userId, enable);
    }

    /**
     * 群组匿名
     */
    public static void setGroupAnonymous(long botId, long groupId, boolean enable) {
        checkService();
        service.setGroupAnonymous(botId, groupId, enable);
    }

    /**
     * 设置群名片
     */
    public static void setGroupCard(long botId, long groupId, long userId, String card) {
        checkService();
        service.setGroupCard(botId, groupId, userId, card);
    }

    /**
     * 设置群名
     */
    public static void setGroupName(long botId, long groupId, String groupName) {
        checkService();
        service.setGroupName(botId, groupId, groupName);
    }

    /**
     * 退出群组
     */
    public static void setGroupLeave(long botId, long groupId, boolean isDismiss) {
        checkService();
        service.setGroupLeave(botId, groupId, isDismiss);
    }

    /**
     * 设置群组专属头衔
     */
    public static void setGroupSpecialTitle(long botId, long groupId, long userId, String specialTitle, long duration) {
        checkService();
        service.setGroupSpecialTitle(botId, groupId, userId, specialTitle, duration);
    }

    /**
     * 处理加好友请求
     */
    public static void setFriendAddRequest(long botId, String flag, boolean approve, String remark) {
        checkService();
        service.setFriendAddRequest(botId, flag, approve, remark);
    }

    /**
     * 处理加群请求／邀请
     */
    public static void setGroupAddRequest(long botId, String flag, String subType, boolean approve, String reason) {
        checkService();
        service.setGroupAddRequest(botId, flag, subType, approve, reason);
    }

    /**
     * 获取登录号信息
     */
    public static LoginInfo getLoginInfo(long botId) {
        checkService();
        return service.getLoginInfo(botId);
    }

    /**
     * 获取陌生人信息
     */
    public static StrangerInfo getStrangerInfo(long botId, long userId, boolean noCache) {
        checkService();
        return service.getStrangerInfo(botId, userId, noCache);
    }

    /**
     * 获取好友列表
     */
    public static List<FriendInfo> getFriendList(long botId) {
        checkService();
        return service.getFriendList(botId);
    }

    /**
     * 获取群列表
     */
    public static List<GroupInfo> getGroupList(long botId) {
        checkService();
        return service.getGroupList(botId);
    }

    /**
     * 获取群成员信息
     */
    public static GroupMemberInfo getGroupMemberInfo(long botId, long groupId, long userId, boolean noCache) {
        checkService();
        return service.getGroupMemberInfo(botId, groupId, userId, noCache);
    }

    /**
     * 获取群成员列表
     */
    public static List<GroupMemberInfo> getGroupMemberList(long botId, long groupId) {
        checkService();
        return service.getGroupMemberList(botId, groupId);
    }

    /**
     * 获取群荣誉信息
     */
    public static GroupHonorInfo getGroupHonorInfo(long botId, long groupId, String type) {
        checkService();
        return service.getGroupHonorInfo(botId, groupId, type);
    }

    /**
     * 获取语音
     *
     * @param file 收到的语音文件名
     * @param outFormat 要转换到的格式
     * @return FileInfo
     */
    public static FileInfo getRecord(long botId, String file, String outFormat) {
        checkService();
        return service.getRecord(botId, file, outFormat);
    }

    /**
     * 获取图片
     *
     * @param file 收到的图片文件名
     * @return FileInfo
     */
    public static FileInfo getImage(long botId, String file) {
        checkService();
        return service.getImage(botId, file);
    }

    /**
     * 检查是否可以发送图片
     */
    public static CanSendInfo canSendImage(long botId) {
        checkService();
        return service.canSendImage(botId);
    }

    /**
     * 检查是否可以发送语音
     */
    public static CanSendInfo canSendRecord(long botId) {
        checkService();
        return service.canSendRecord(botId);
    }

    /**
     * 通用 API 调用方法 (返回 void)
     */
    public static void callApiVoid(long botId, String action, Map<String, Object> params) {
        checkService();
        service.callApiVoid(botId, action, params);
    }

    /**
     * 通用 API 调用方法 (返回 List)
     */
    public static <T> List<T> callApiList(long botId, String action, Map<String, Object> params, Class<T> elementType) {
        checkService();
        return service.callApiList(botId, action, params, elementType);
    }

    /**
     * 通用 API 调用方法 (返回指定类型)
     */
    public static <T> T callApi(long botId, String action, Map<String, Object> params, Class<T> responseType) {
        checkService();
        return service.callApi(botId, action, params, responseType);
    }
}
