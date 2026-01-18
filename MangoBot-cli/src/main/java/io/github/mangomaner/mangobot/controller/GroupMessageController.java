package io.github.mangomaner.mangobot.controller;

import io.github.mangomaner.mangobot.common.BaseResponse;
import io.github.mangomaner.mangobot.common.ResultUtils;
import io.github.mangomaner.mangobot.model.domain.GroupMessages;
import io.github.mangomaner.mangobot.model.dto.message.QueryLatestMessagesRequest;
import io.github.mangomaner.mangobot.model.dto.message.QueryMessagesByMessageIdRequest;
import io.github.mangomaner.mangobot.model.dto.message.QueryMessagesBySenderRequest;
import io.github.mangomaner.mangobot.model.dto.message.SearchMessagesRequest;
import io.github.mangomaner.mangobot.model.vo.GroupMessageVO;
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
    public BaseResponse<List<GroupMessageVO>> getLatestMessages(QueryLatestMessagesRequest request) {
        return ResultUtils.success(groupMessagesService.convertToVOList(groupMessagesService.getLatestMessages(request)));
    }

    @GetMapping("/more/byMessageId")
    public BaseResponse<List<GroupMessageVO>> getMoreMessagesByMessageId(QueryMessagesByMessageIdRequest request) {
        return ResultUtils.success(groupMessagesService.convertToVOList(groupMessagesService.getMessagesByMessageId(request)));
    }

    @GetMapping("/bySender")
    public BaseResponse<List<GroupMessageVO>> getMessagesBySender(QueryMessagesBySenderRequest request) {
        return ResultUtils.success(groupMessagesService.convertToVOList(groupMessagesService.getMessagesBySender(request)));
    }

    @GetMapping("/search")
    public BaseResponse<List<GroupMessageVO>> searchMessages(SearchMessagesRequest request) {
        return ResultUtils.success(groupMessagesService.convertToVOList(groupMessagesService.searchMessages(request)));
    }

    @GetMapping("/id/{id}")
    public BaseResponse<GroupMessageVO> getMessageById(Integer id) {
        return ResultUtils.success(groupMessagesService.convertToVO(groupMessagesService.getMessageById(id)));
    }
}
