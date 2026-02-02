package io.github.mangomaner.mangobot.api;

import io.github.mangomaner.mangobot.model.domain.BotFiles;
import io.github.mangomaner.mangobot.model.dto.UpdateFileRequest;
import io.github.mangomaner.mangobot.service.BotFilesService;

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

    public static List<BotFiles> getFilesByType(String fileType) {
        checkService();
        return service.getFilesByType(fileType);
    }

    public static Boolean updateFile(UpdateFileRequest request) {
        checkService();
        return service.updateFile(request);
    }
}
