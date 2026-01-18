package io.github.mangomaner.mangobot.controller;

import io.github.mangomaner.mangobot.common.BaseResponse;
import io.github.mangomaner.mangobot.common.ResultUtils;
import io.github.mangomaner.mangobot.model.domain.PrivateMessages;
import io.github.mangomaner.mangobot.model.dto.message.QueryLatestMessagesRequest;
import io.github.mangomaner.mangobot.model.dto.message.QueryMessagesByMessageIdRequest;
import io.github.mangomaner.mangobot.model.dto.message.QueryMessagesBySenderRequest;
import io.github.mangomaner.mangobot.model.dto.message.SearchMessagesRequest;
import io.github.mangomaner.mangobot.model.vo.PrivateMessageVO;
import io.github.mangomaner.mangobot.service.PrivateMessagesService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/message/private")
@Slf4j
public class PrivateMessageController {
    @Resource
    private PrivateMessagesService privateMessagesService;
    @GetMapping("/getLatestMessages")
    public BaseResponse<List<PrivateMessageVO>> getLatestMessages(QueryLatestMessagesRequest request) {
        return ResultUtils.success(privateMessagesService.convertToVOList(privateMessagesService.getLatestMessages(request)));
    }

    @GetMapping("/more/byMessageId")
    public BaseResponse<List<PrivateMessageVO>> getMessagesByMessageId(QueryMessagesByMessageIdRequest request) {
        return ResultUtils.success(privateMessagesService.convertToVOList(privateMessagesService.getMessagesByMessageId(request)));
    }

    @GetMapping("/bySender")
    public BaseResponse<List<PrivateMessageVO>> getMessagesBySender(QueryMessagesBySenderRequest request) {
        return ResultUtils.success(privateMessagesService.convertToVOList(privateMessagesService.getMessagesBySender(request)));
    }

    @GetMapping("/search")
    public BaseResponse<List<PrivateMessageVO>> searchMessages(SearchMessagesRequest request) {
        return ResultUtils.success(privateMessagesService.convertToVOList(privateMessagesService.searchMessages(request)));
    }

    @GetMapping("/id/{id}")
    public BaseResponse<PrivateMessageVO> getMessageById(Integer id) {
        return ResultUtils.success(privateMessagesService.convertToVO(privateMessagesService.getMessageById(id)));
    }
}
