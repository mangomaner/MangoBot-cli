package io.github.mangomaner.mangobot.service;

import io.github.mangomaner.mangobot.model.domain.BotFiles;
import io.github.mangomaner.mangobot.model.dto.AddFileRequest;
import io.github.mangomaner.mangobot.model.dto.UpdateFileRequest;
import com.baomidou.mybatisplus.extension.service.IService;
import io.github.mangomaner.mangobot.model.onebot.segment.MessageSegment;

import java.util.List;

/**
* @author mangoman
* @description 针对表【files】的数据库操作Service
* @createDate 2026-01-17 23:40:10
*/
public interface BotFilesService extends IService<BotFiles> {

    List<BotFiles> getAllFiles();

    BotFiles getFileById(Long id);

    BotFiles getFileByFileId(String fileId);

    List<BotFiles> getFilesByType(String fileType);

    Boolean addFile(AddFileRequest request);

    Boolean updateFile(UpdateFileRequest request);

    Boolean deleteFile(Long id);

    Boolean deleteFileByFileId(String fileId);

    void saveFileBySegments(List<MessageSegment> segments);
}
