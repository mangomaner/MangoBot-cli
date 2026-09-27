package io.github.mangomaner.mangobot.api;

import io.github.mangomaner.mangobot.module.file.model.domain.BotFiles;
import io.github.mangomaner.mangobot.module.file.model.dto.UpdateFileRequest;
import io.github.mangomaner.mangobot.module.file.service.BotFilesService;

import java.util.List;

/**
 * 文件 API (静态工具类)
 * 提供对文件的查询和更新能力，不允许新增和删除。
 */
public class MangoFileApi {

    private static BotFilesService service;

    private MangoFileApi() {}

    static void setService(BotFilesService service) {
        MangoFileApi.service = service;
    }

    private static void checkService() {
        if (service == null) {
            throw new IllegalStateException("MangoFileApi has not been initialized yet.");
        }
    }

    public static List<BotFiles> getAllFiles() {
        checkService();
        return service.getAllFiles();
    }

    public static List<BotFiles> getFilesByDescription(String description) {
        checkService();
        return service.getFilesByDescription(description);
    }

    public static BotFiles getFileById(Long id) {
        checkService();
        return service.getFileById(id);
    }

    public static BotFiles getFileByFileId(String fileId) {
        checkService();
        return service.getFileByFileId(fileId);
    }

    /**
     * agent 发表情唯一能用的池子：已收藏、有本地文件的 meme（不要求有描述）。
     * 收藏为空时返回空列表——发表情的能力就是关着的，不做回退。
     */
    public static List<BotFiles> getFavoriteMemes() {
        checkService();
        return service.listFavoriteMemes();
    }

    public static List<BotFiles> getFilesByType(String fileType) {
        checkService();
        return service.getFilesByType(fileType);
    }

    public static Boolean updateFile(UpdateFileRequest request) {
        checkService();
        return service.updateFile(request);
    }
}
