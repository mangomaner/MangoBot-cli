package org.mango.mangobot.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.mango.mangobot.common.BaseResponse;
import org.mango.mangobot.common.ResultUtils;
import org.mango.mangobot.model.onebot.SendMessage;
import org.mango.mangobot.model.onebot.MessageBuilder;
import org.mango.mangobot.model.onebot.api.response.*;
import org.mango.mangobot.service.OneBotApiService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/test")
@Tag(name = "OneBot API 测试接口", description = "用于测试 OneBotApiService 的各项功能")
@Slf4j
public class ApiTestController {

    private final OneBotApiService oneBotApiService;
    private final ObjectMapper objectMapper;

    public ApiTestController(OneBotApiService oneBotApiService, ObjectMapper objectMapper) {
        this.oneBotApiService = oneBotApiService;
        this.objectMapper = objectMapper;
    }

    private <T> T parse(String json, Class<T> clazz) {
        try {
            if (json == null) return null;
            return objectMapper.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            log.error("JSON parse error", e);
            throw new RuntimeException(e);
        }
    }
    
    private <T> List<T> parseList(String json, Class<T> clazz) {
        try {
            if (json == null) return null;
            return objectMapper.readValue(json, objectMapper.getTypeFactory().constructCollectionType(List.class, clazz));
        } catch (JsonProcessingException e) {
            log.error("JSON parse error", e);
            throw new RuntimeException(e);
        }
    }

    @GetMapping("/aaa")
    @Operation(summary = "测试 aaa")
    public BaseResponse<MessageId> aaa() {
        SendMessage builder = MessageBuilder.create()
                .customNode("1461626638", "哈哈", "[CQ:face,id=123]哈喽～")
                .build();
        SendMessage builder1 = MessageBuilder.create()
                .image("https://www.qcyqq.com/wp-content/uploads/60s/old-2024-07-03.png", 1)
                .build();
        String json = oneBotApiService.sendGroupMsg(1461626638, 220264051, builder1);
        return ResultUtils.success(parse(json, MessageId.class));
    }

    @PostMapping("/sendGroupForwardMsg")
    @Operation(summary = "发送合并转发消息 (群)")
    public BaseResponse<MessageId> sendGroupForwardMsg(@RequestParam long botId, @RequestParam long groupId, @RequestBody Object messages) {
        String json = oneBotApiService.sendGroupForwardMsg(botId, groupId, messages);
        return ResultUtils.success(parse(json, MessageId.class));
    }

    @PostMapping("/sendPrivateForwardMsg")
    @Operation(summary = "发送合并转发消息 (私聊)")
    public BaseResponse<MessageId> sendPrivateForwardMsg(@RequestParam long botId, @RequestParam long userId, @RequestBody Object messages) {
        String json = oneBotApiService.sendPrivateForwardMsg(botId, userId, messages);
        return ResultUtils.success(parse(json, MessageId.class));
    }

    @PostMapping("/sendPrivateMsg")
    @Operation(summary = "发送私聊消息")
    public BaseResponse<MessageId> sendPrivateMsg(@RequestParam long botId, @RequestParam long userId, @RequestParam String message) {
        log.info("测试发送私聊消息: botId={}, userId={}, message={}", botId, userId, message);
        SendMessage sendMessage = MessageBuilder.create()
                .text(message)
                .build();
        String json = oneBotApiService.sendPrivateMsg(botId, userId, sendMessage);
        return ResultUtils.success(parse(json, MessageId.class));
    }

    @PostMapping("/sendGroupMsg")
    @Operation(summary = "发送群消息")
    public BaseResponse<MessageId> sendGroupMsg(@RequestParam long botId, @RequestParam long groupId, @RequestParam String message) {
        log.info("测试发送群消息: botId={}, groupId={}, message={}", botId, groupId, message);
        SendMessage sendMessage = MessageBuilder.create()
                .text(message)
                .build();
        String json = oneBotApiService.sendGroupMsg(botId, groupId, sendMessage);
        return ResultUtils.success(parse(json, MessageId.class));
    }

    @PostMapping("/sendComplexGroupMsg")
    @Operation(summary = "发送复杂群消息 (Text + At + Image)")
    public BaseResponse<MessageId> sendComplexGroupMsg(@RequestParam long botId, @RequestParam long groupId, 
                                                    @RequestParam String text, @RequestParam String atQq, @RequestParam String imageUrl) {
        log.info("测试发送复杂群消息: botId={}, groupId={}", botId, groupId);
        
        SendMessage builder = MessageBuilder.create()
                .at(atQq)
                .text(" " + text)
                .image(imageUrl, 1)
                .build();
        
        String json = oneBotApiService.sendGroupMsg(botId, groupId, builder);
        return ResultUtils.success(parse(json, MessageId.class));
    }

    @PostMapping("/deleteMsg")
    @Operation(summary = "撤回消息")
    public BaseResponse<Void> deleteMsg(@RequestParam long botId, @RequestParam int messageId) {
        log.info("测试撤回消息: botId={}, messageId={}", botId, messageId);
        oneBotApiService.deleteMsg(botId, messageId);
        return ResultUtils.success(null);
    }

    @GetMapping("/getGroupInfo")
    @Operation(summary = "获取群信息")
    public BaseResponse<GroupInfo> getGroupInfo(@RequestParam long botId, @RequestParam long groupId, @RequestParam(defaultValue = "false") boolean noCache) {
        log.info("测试获取群信息: botId={}, groupId={}, noCache={}", botId, groupId, noCache);
        String json = oneBotApiService.getGroupInfo(botId, groupId, noCache);
        return ResultUtils.success(parse(json, GroupInfo.class));
    }

    @PostMapping("/sendMsg")
    @Operation(summary = "发送消息 (通用)")
    public BaseResponse<MessageId> sendMsg(@RequestParam long botId, @RequestParam String messageType, @RequestParam(required = false) Long userId, @RequestParam(required = false) Long groupId, @RequestParam String message) {
        String json = oneBotApiService.sendMsg(botId, messageType, userId, groupId, message);
        return ResultUtils.success(parse(json, MessageId.class));
    }

    @GetMapping("/getMsg")
    @Operation(summary = "获取消息")
    public BaseResponse<MessageInfo> getMsg(@RequestParam long botId, @RequestParam int messageId) {
        String json = oneBotApiService.getMsg(botId, messageId);
        return ResultUtils.success(parse(json, MessageInfo.class));
    }

    @GetMapping("/getLoginInfo")
    @Operation(summary = "获取登录号信息")
    public BaseResponse<LoginInfo> getLoginInfo(@RequestParam long botId) {
        String json = oneBotApiService.getLoginInfo(botId);
        return ResultUtils.success(parse(json, LoginInfo.class));
    }

    @GetMapping("/getFriendList")
    @Operation(summary = "获取好友列表")
    public BaseResponse<List<FriendInfo>> getFriendList(@RequestParam long botId) {
        String json = oneBotApiService.getFriendList(botId);
        return ResultUtils.success(parseList(json, FriendInfo.class));
    }

    @GetMapping("/getGroupList")
    @Operation(summary = "获取群列表")
    public BaseResponse<List<GroupInfo>> getGroupList(@RequestParam long botId) {
        String json = oneBotApiService.getGroupList(botId);
        return ResultUtils.success(parseList(json, GroupInfo.class));
    }

    @GetMapping("/getGroupMemberInfo")
    @Operation(summary = "获取群成员信息")
    public BaseResponse<GroupMemberInfo> getGroupMemberInfo(@RequestParam long botId, @RequestParam long groupId, @RequestParam long userId, @RequestParam(defaultValue = "false") boolean noCache) {
        String json = oneBotApiService.getGroupMemberInfo(botId, groupId, userId, noCache);
        return ResultUtils.success(parse(json, GroupMemberInfo.class));
    }

    @GetMapping("/getGroupMemberList")
    @Operation(summary = "获取群成员列表")
    public BaseResponse<List<GroupMemberInfo>> getGroupMemberList(@RequestParam long botId, @RequestParam long groupId) {
        String json = oneBotApiService.getGroupMemberList(botId, groupId);
        return ResultUtils.success(parseList(json, GroupMemberInfo.class));
    }

    @GetMapping("/getGroupHonorInfo")
    @Operation(summary = "获取群荣誉信息")
    public BaseResponse<GroupHonorInfo> getGroupHonorInfo(@RequestParam long botId, @RequestParam long groupId, @RequestParam String type) {
        String json = oneBotApiService.getGroupHonorInfo(botId, groupId, type);
        return ResultUtils.success(parse(json, GroupHonorInfo.class));
    }

    @GetMapping("/getRecord")
    @Operation(summary = "获取语音")
    public BaseResponse<FileInfo> getRecord(@RequestParam long botId, @RequestParam String file, @RequestParam String outFormat) {
        String json = oneBotApiService.getRecord(botId, file, outFormat);
        return ResultUtils.success(parse(json, FileInfo.class));
    }

    @GetMapping("/getImage")
    @Operation(summary = "获取图片")
    public BaseResponse<FileInfo> getImage(@RequestParam long botId, @RequestParam String file) {
        String json = oneBotApiService.getImage(botId, file);
        return ResultUtils.success(parse(json, FileInfo.class));
    }

    @GetMapping("/canSendImage")
    @Operation(summary = "检查是否可以发送图片")
    public BaseResponse<CanSendInfo> canSendImage(@RequestParam long botId) {
        String json = oneBotApiService.canSendImage(botId);
        return ResultUtils.success(parse(json, CanSendInfo.class));
    }

    @GetMapping("/canSendRecord")
    @Operation(summary = "检查是否可以发送语音")
    public BaseResponse<CanSendInfo> canSendRecord(@RequestParam long botId) {
        String json = oneBotApiService.canSendRecord(botId);
        return ResultUtils.success(parse(json, CanSendInfo.class));
    }

    @PostMapping("/sendLike")
    @Operation(summary = "发送好友赞")
    public BaseResponse<Void> sendLike(@RequestParam long botId, @RequestParam long userId, @RequestParam int times) {
        oneBotApiService.sendLike(botId, userId, times);
        return ResultUtils.success(null);
    }

    @PostMapping("/setGroupKick")
    @Operation(summary = "群组踢人")
    public BaseResponse<Void> setGroupKick(@RequestParam long botId, @RequestParam long groupId, @RequestParam long userId, @RequestParam(defaultValue = "false") boolean rejectAddRequest) {
        oneBotApiService.setGroupKick(botId, groupId, userId, rejectAddRequest);
        return ResultUtils.success(null);
    }

    @PostMapping("/setGroupBan")
    @Operation(summary = "群组单人禁言")
    public BaseResponse<Void> setGroupBan(@RequestParam long botId, @RequestParam long groupId, @RequestParam long userId, @RequestParam(defaultValue = "1800") long duration) {
        oneBotApiService.setGroupBan(botId, groupId, userId, duration);
        return ResultUtils.success(null);
    }

    @PostMapping("/setGroupWholeBan")
    @Operation(summary = "群组全员禁言")
    public BaseResponse<Void> setGroupWholeBan(@RequestParam long botId, @RequestParam long groupId, @RequestParam(defaultValue = "true") boolean enable) {
        oneBotApiService.setGroupWholeBan(botId, groupId, enable);
        return ResultUtils.success(null);
    }

    @PostMapping("/setGroupAdmin")
    @Operation(summary = "群组设置管理员")
    public BaseResponse<Void> setGroupAdmin(@RequestParam long botId, @RequestParam long groupId, @RequestParam long userId, @RequestParam(defaultValue = "true") boolean enable) {
        oneBotApiService.setGroupAdmin(botId, groupId, userId, enable);
        return ResultUtils.success(null);
    }

    @PostMapping("/setGroupAnonymous")
    @Operation(summary = "群组匿名")
    public BaseResponse<Void> setGroupAnonymous(@RequestParam long botId, @RequestParam long groupId, @RequestParam(defaultValue = "true") boolean enable) {
        oneBotApiService.setGroupAnonymous(botId, groupId, enable);
        return ResultUtils.success(null);
    }

    @PostMapping("/setGroupCard")
    @Operation(summary = "设置群名片")
    public BaseResponse<Void> setGroupCard(@RequestParam long botId, @RequestParam long groupId, @RequestParam long userId, @RequestParam(required = false) String card) {
        oneBotApiService.setGroupCard(botId, groupId, userId, card == null ? "" : card);
        return ResultUtils.success(null);
    }

    @PostMapping("/setGroupName")
    @Operation(summary = "设置群名")
    public BaseResponse<Void> setGroupName(@RequestParam long botId, @RequestParam long groupId, @RequestParam String groupName) {
        oneBotApiService.setGroupName(botId, groupId, groupName);
        return ResultUtils.success(null);
    }

    @PostMapping("/setGroupLeave")
    @Operation(summary = "退出群组")
    public BaseResponse<Void> setGroupLeave(@RequestParam long botId, @RequestParam long groupId, @RequestParam(defaultValue = "false") boolean isDismiss) {
        oneBotApiService.setGroupLeave(botId, groupId, isDismiss);
        return ResultUtils.success(null);
    }

    @PostMapping("/setGroupSpecialTitle")
    @Operation(summary = "设置群组专属头衔")
    public BaseResponse<Void> setGroupSpecialTitle(@RequestParam long botId, @RequestParam long groupId, @RequestParam long userId, @RequestParam(required = false) String specialTitle, @RequestParam(defaultValue = "-1") long duration) {
        oneBotApiService.setGroupSpecialTitle(botId, groupId, userId, specialTitle == null ? "" : specialTitle, duration);
        return ResultUtils.success(null);
    }

    @PostMapping("/setFriendAddRequest")
    @Operation(summary = "处理加好友请求")
    public BaseResponse<Void> setFriendAddRequest(@RequestParam long botId, @RequestParam String flag, @RequestParam(defaultValue = "true") boolean approve, @RequestParam(required = false) String remark) {
        oneBotApiService.setFriendAddRequest(botId, flag, approve, remark == null ? "" : remark);
        return ResultUtils.success(null);
    }

    @PostMapping("/setGroupAddRequest")
    @Operation(summary = "处理加群请求／邀请")
    public BaseResponse<Void> setGroupAddRequest(@RequestParam long botId, @RequestParam String flag, @RequestParam String subType, @RequestParam(defaultValue = "true") boolean approve, @RequestParam(required = false) String reason) {
        oneBotApiService.setGroupAddRequest(botId, flag, subType, approve, reason == null ? "" : reason);
        return ResultUtils.success(null);
    }
}
