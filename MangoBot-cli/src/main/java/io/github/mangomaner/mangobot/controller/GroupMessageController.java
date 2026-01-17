package io.github.mangomaner.mangobot.controller;

import io.github.mangomaner.mangobot.common.BaseResponse;
import io.github.mangomaner.mangobot.common.ResultUtils;
import io.github.mangomaner.mangobot.model.domain.GroupMessages;
import io.github.mangomaner.mangobot.model.dto.message.QueryLatestMessagesRequest;
import io.github.mangomaner.mangobot.model.dto.message.QueryMessagesByMessageIdRequest;
import io.github.mangomaner.mangobot.model.dto.message.QueryMessagesBySenderRequest;
import io.github.mangomaner.mangobot.model.dto.message.SearchMessagesRequest;
import io.github.mangomaner.mangobot.service.GroupMessagesService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/message/group")
@Slf4j
public class GroupMessageController {
    @Resource
    private GroupMessagesService groupMessagesService;
    @GetMapping("/getLatestMessages")
    public BaseResponse<List<GroupMessages>> getLatestMessages(QueryLatestMessagesRequest request) {
        return ResultUtils.success(groupMessagesService.getLatestMessages(request));
    }
    @GetMapping("/more/byMessageId")
    public BaseResponse<List<GroupMessages>> getMoreMessagesByMessageId(QueryMessagesByMessageIdRequest request) {
        return ResultUtils.success(groupMessagesService.getMessagesByMessageId(request));
    }
}
