package org.mango.mangobot.controller;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.mango.mangobot.common.BaseResponse;
import org.mango.mangobot.common.ResultUtils;
import org.mango.mangobot.service.impl.GroupMessageService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/test/groupMessage")
@Slf4j
public class GroupMessageServiceTest {

    @Resource
    private GroupMessageService groupMessageService;

    /**
     * 发送纯文本消息
     */
    @GetMapping("/sendText")
    public BaseResponse<String> sendTextMessage(
            @RequestParam String selfId,
            @RequestParam String groupId,
            @RequestParam String text) {
        groupMessageService.sendTextMessage(groupId, text);
        return ResultUtils.success("文本消息已发送: " + text);
    }

    /**
     * 发送带 @ 的消息
     */
    @GetMapping("/sendAt")
    public BaseResponse<String> sendAtMessage(
            @RequestParam String selfId,
            @RequestParam String groupId,
            @RequestParam String qq,
            @RequestParam String text) {
        groupMessageService.sendAtMessage(groupId, qq, text);
        return ResultUtils.success("@消息已发送: @" + qq + " " + text);
    }

    /**
     * 发送图片消息
     */
    @GetMapping("/sendImage")
    public BaseResponse<String> sendImageMessage(
            @RequestParam String selfId,
            @RequestParam String groupId,
            @RequestParam String fileUrlOrPath) {
        groupMessageService.sendImageMessage(groupId, fileUrlOrPath);
        return ResultUtils.success("图片消息已发送: " + fileUrlOrPath);
    }

    /**
     * 发送语音消息
     */
    @GetMapping("/sendRecord")
    public BaseResponse<String> sendRecordMessage(
            @RequestParam String selfId,
            @RequestParam String groupId,
            @RequestParam String fileUrlOrPath) {
        groupMessageService.sendRecordMessage(groupId, fileUrlOrPath);
        return ResultUtils.success("语音消息已发送: " + fileUrlOrPath);
    }

    /**
     * 发送回复消息
     */
    @GetMapping("/sendReply")
    public BaseResponse<String> sendReplyMessage(
            @RequestParam String selfId,
            @RequestParam String groupId,
            @RequestParam String replyMessageId,
            @RequestParam String message) {
        groupMessageService.sendReplyMessage(groupId, replyMessageId, message);
        return ResultUtils.success("回复消息已发送，ID: " + replyMessageId + message);
    }

    /**
     * 发送混合消息（文本 + @ + 图片），不需要的可以不传
     */
    @GetMapping("/sendMixed")
    public BaseResponse<String> sendCustomMixedMessage(
            @RequestParam String selfId,
            @RequestParam String groupId,
            @RequestParam(required = false) String text,
            @RequestParam(required = false) String qq,
            @RequestParam(required = false) String imageUrl) {

        groupMessageService.sendCustomMessage(groupId, text, qq, imageUrl);
        return ResultUtils.success("混合消息已发送");
    }
}