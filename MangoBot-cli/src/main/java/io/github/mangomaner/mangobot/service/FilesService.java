package io.github.mangomaner.mangobot.service;

import io.github.mangomaner.mangobot.model.domain.Files;
import io.github.mangomaner.mangobot.model.dto.AddFileRequest;
import io.github.mangomaner.mangobot.model.dto.UpdateFileRequest;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
* @author mangoman
* @description 针对表【files】的数据库操作Service
* @createDate 2026-01-17 23:40:10
*/
public interface FilesService extends IService<Files> {

    List<Files> getAllFiles();

    Files getFileById(Long id);

    Files getFileByFileId(String fileId);

    List<Files> getFilesByType(String fileType);

    Boolean addFile(AddFileRequest request);

    Boolean updateFile(UpdateFileRequest request);

    Boolean deleteFile(Long id);

    Boolean deleteFileByFileId(String fileId);
}
