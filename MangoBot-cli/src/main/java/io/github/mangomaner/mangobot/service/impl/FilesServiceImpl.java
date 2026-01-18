package io.github.mangomaner.mangobot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import io.github.mangomaner.mangobot.model.domain.Files;
import io.github.mangomaner.mangobot.model.dto.AddFileRequest;
import io.github.mangomaner.mangobot.model.dto.UpdateFileRequest;
import io.github.mangomaner.mangobot.model.onebot.segment.*;
import io.github.mangomaner.mangobot.service.FilesService;
import io.github.mangomaner.mangobot.mapper.FilesMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
* @author mangoman
* @description 针对表【files】的数据库操作Service实现
* @createDate 2026-01-17 23:40:10
*/
@Service
public class FilesServiceImpl extends ServiceImpl<FilesMapper, Files>
    implements FilesService {

    @Override
    public List<Files> getAllFiles() {
        return this.list();
    }

    @Override
    public Files getFileById(Long id) {
        return this.getById(id);
    }

    @Override
    public Files getFileByFileId(String fileId) {
        LambdaQueryWrapper<Files> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Files::getFileId, fileId);
        return this.getOne(wrapper);
    }

    @Override
    public List<Files> getFilesByType(String fileType) {
        LambdaQueryWrapper<Files> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Files::getFileType, fileType);
        return this.list(wrapper);
    }

    @Override
    public Boolean addFile(AddFileRequest request) {
        if (this.getFileByFileId(request.getFileId()) != null) {
            return false;
        }
        Files files = new Files();
        files.setFileType(request.getFileType());
        files.setFileId(request.getFileId());
        files.setUrl(request.getUrl());
        files.setFilePath(request.getFilePath());
        files.setSubType(request.getSubType());
        files.setFileSize(request.getFileSize());
        files.setDescription(request.getDescription());
        files.setCreateTime(System.currentTimeMillis());
        return this.save(files);
    }

    @Override
    public Boolean updateFile(UpdateFileRequest request) {
        Files files = this.getById(request.getId());
        if (files == null) {
            return false;
        }
        if (request.getFileType() != null) {
            files.setFileType(request.getFileType());
        }
        if (request.getUrl() != null) {
            files.setUrl(request.getUrl());
        }
        if (request.getFilePath() != null) {
            files.setFilePath(request.getFilePath());
        }
        if (request.getSubType() != null) {
            files.setSubType(request.getSubType());
        }
        if (request.getFileSize() != null) {
            files.setFileSize(request.getFileSize());
        }
        if (request.getDescription() != null) {
            files.setDescription(request.getDescription());
        }
        return this.updateById(files);
    }

    @Override
    public Boolean deleteFile(Long id) {
        return this.removeById(id);
    }

    @Override
    public Boolean deleteFileByFileId(String fileId) {
        LambdaQueryWrapper<Files> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Files::getFileId, fileId);
        return this.remove(wrapper);
    }

    /**
     * 保存文件到数据库
     * @param segments
     * @return
     */
    @Override
    public void saveFileBySegments(List<MessageSegment> segments) {

        for (MessageSegment segment : segments) {
            if (segment instanceof FileSegment) {
                AddFileRequest request = new AddFileRequest();
                FileSegment.FileData data = ((FileSegment) segment).getData();
                request.setFileId(data.getFileId());
                request.setFileType("file");
                request.setUrl(data.getUrl());
                request.setFileSize(Integer.parseInt(data.getFileSize()));
                request.setDescription(data.getFile());
                this.addFile(request);
            } else if (segment instanceof ImageSegment) {
                ImageSegment.ImageData data = ((ImageSegment) segment).getData();

                int subType = data.getSubType();
                String url = data.getUrl();
                AddFileRequest request = new AddFileRequest();
                request.setFileId(data.getFile());
                request.setUrl(url);
                request.setSubType(subType);
                request.setFileSize(Integer.parseInt(data.getFileSize()));
                switch (subType) {
                    case 0 ->                           // 发送的手机图片，0为普通图片
                            request.setFileType("image");
                    case 1, 11 ->                       // 1为QQ收藏的表情包，11为发送的gif图片
                            request.setFileType("meme");
                    default -> request.setFileType("image");
                }
                this.addFile(request);
            } else if (segment instanceof VideoSegment) {
                VideoSegment.VideoData data = ((VideoSegment) segment).getData();
                AddFileRequest request = new AddFileRequest();
                request.setFileId(data.getFile());
                request.setFileType("video");
                request.setUrl(data.getUrl());
                this.addFile(request);
            } else if (segment instanceof RecordSegment) {
                RecordSegment.RecordData data = ((RecordSegment) segment).getData();
                AddFileRequest request = new AddFileRequest();
                request.setFileId(data.getFile());
                request.setFileType("record");
                request.setUrl(data.getUrl());
                this.addFile(request);
            }
        }
    }
}




