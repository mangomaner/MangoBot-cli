package io.github.mangomaner.mangobot.controller;

import io.github.mangomaner.mangobot.common.BaseResponse;
import io.github.mangomaner.mangobot.common.ResultUtils;
import io.github.mangomaner.mangobot.model.domain.PrivateMessages;
import io.github.mangomaner.mangobot.model.dto.message.QueryLatestMessagesRequest;
import io.github.mangomaner.mangobot.model.dto.message.QueryMessagesByMessageIdRequest;
import io.github.mangomaner.mangobot.service.PrivateMessagesService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/message/private")
@Slf4j
public class PrivateMessageController {
    @Resource
    private PrivateMessagesService privateMessagesService;
    @RequestMapping("/getLatestMessages")
    public BaseResponse<List<PrivateMessages>> getLatestMessages(QueryLatestMessagesRequest request) {
        return ResultUtils.success(privateMessagesService.getLatestMessages(request));
    }
    @RequestMapping("/more/byMessageId")
    public BaseResponse<List<PrivateMessages>> getMessagesByMessageId(QueryMessagesByMessageIdRequest request) {
        return ResultUtils.success(privateMessagesService.getMessagesByMessageId(request));
    }
}
