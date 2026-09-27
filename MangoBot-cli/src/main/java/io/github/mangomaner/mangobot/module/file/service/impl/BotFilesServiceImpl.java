package io.github.mangomaner.mangobot.module.file.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import io.github.mangomaner.mangobot.module.file.model.domain.BotFiles;
import io.github.mangomaner.mangobot.system.mapper.BotFilesMapper;
import io.github.mangomaner.mangobot.module.file.model.dto.AddFileRequest;
import io.github.mangomaner.mangobot.module.file.model.dto.UpdateFileRequest;
import io.github.mangomaner.mangobot.module.file.service.BotFilesService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
* @author mangoman
* @description 针对表【files】的数据库操作Service实现
* @createDate 2026-01-17 23:40:10
*/
@Service
@Slf4j
public class BotFilesServiceImpl extends ServiceImpl<BotFilesMapper, BotFiles>
    implements BotFilesService {

    @Override
    public List<BotFiles> getAllFiles() {
        return this.list();
    }

    @Override
    public List<BotFiles> getFilesByDescription(String description) {
        LambdaQueryWrapper<BotFiles> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(BotFiles::getDescription, description);
        return this.list(wrapper);
    }

    @Override
    public BotFiles getFileById(Long id) {
        return this.getById(id);
    }

    @Override
    public BotFiles getFileByFileId(String fileId) {
        LambdaQueryWrapper<BotFiles> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BotFiles::getFileId, fileId);
        return this.getOne(wrapper);
    }

    @Override
    public List<BotFiles> getFilesByType(String fileType) {
        LambdaQueryWrapper<BotFiles> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BotFiles::getFileType, fileType);
        return this.list(wrapper);
    }

    /** 发表情能用的池子：被收藏、本地有文件的 meme。描述不作要求（见下）。 */
    @Override
    public List<BotFiles> listFavoriteMemes() {
        return this.list(favoriteMemesWrapper());
    }

    /**
     * 池子的取数条件，抽出来是为了能被单元测试直接断言（不连库）。
     *
     * <p>为什么**不**要求描述非空：描述是"模型看不了图"时代的替代品，只用于给 keyword 打分。
     * 现在模型能直接看图（list_memes），没描述的图照样挑得中；要求描述只会把大量人工收藏的图
     * 挡在外面——实测用户收藏的 13 张里没有一张有描述，池子直接空了。
     * 需要描述的那条路（只给 keyword、不看图）自己再筛一次。
     *
     * <p>{@code favorite=1} 这条最关键：丢了它 bot 就退回"从 1800 多张没筛过的图里挑"，
     * 而且不报错、看不出来。
     */
    static LambdaQueryWrapper<BotFiles> favoriteMemesWrapper() {
        LambdaQueryWrapper<BotFiles> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BotFiles::getFileType, "meme");
        wrapper.eq(BotFiles::getFavorite, 1);
        wrapper.isNotNull(BotFiles::getFilePath);
        wrapper.ne(BotFiles::getFilePath, "");
        return wrapper;
    }

    @Override
    public Boolean addFile(AddFileRequest request) {
        if (this.getFileByFileId(request.getFileId()) != null) {
            return false;
        }
        BotFiles files = new BotFiles();
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
        BotFiles files = this.getById(request.getId());
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
        LambdaQueryWrapper<BotFiles> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BotFiles::getFileId, fileId);
        return this.remove(wrapper);
    }
}
