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

    /**
     * 发表情的唯一取数口：被收藏、有描述、本地有文件的 meme。
     *
     * <p>三个条件是缺一不可的，各自挡掉一类东西：
     * <ul>
     *   <li>{@code favorite=1} —— 人工在表情管理页设的质量闸门。库里上千个 meme 是群里捡来的，
     *       没有这层筛选，bot 会挑出垃圾图和不合时宜的图。</li>
     *   <li>{@code description} 非空 —— 挑图是按描述与 keyword 的命中打分做的，没描述的图
     *       既没法打分、发出去也没法预期效果。</li>
     *   <li>{@code file_path} 非空 —— 发送要读本地字节，没有落地的文件发不出去。</li>
     * </ul>
     *
     * <p>为什么不写成「取全部再在 Java 里 filter」：meme 有 1800+ 行，而这个池子通常只有几十张，
     * 每次发表情都把全表拉进内存不值得。条件放进 SQL，走索引。
     */
    @Override
    public List<BotFiles> listUsableFavoriteMemes() {
        return this.list(usableFavoriteMemesWrapper());
    }

    /**
     * 上面那五个条件的构造，单独抽出来是为了能被单元测试直接断言（不需要数据库）。
     *
     * <p>这五个条件里丢掉任何一个都会静默降级而不是报错——尤其 {@code favorite=1}：
     * 少了它，bot 就退回"从 1800 多张没筛选过的图里挑"，而这件事在运行期看不出来（照样能出图，
     * 只是图变脏了）。所以值得钉住。
     */
    static LambdaQueryWrapper<BotFiles> usableFavoriteMemesWrapper() {
        LambdaQueryWrapper<BotFiles> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BotFiles::getFileType, "meme");
        wrapper.eq(BotFiles::getFavorite, 1);
        wrapper.isNotNull(BotFiles::getDescription);
        wrapper.ne(BotFiles::getDescription, "");
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
