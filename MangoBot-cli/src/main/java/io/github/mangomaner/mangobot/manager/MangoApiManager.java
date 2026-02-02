package io.github.mangomaner.mangobot.manager;

import io.github.mangomaner.mangobot.api.MangoFileApi;
import io.github.mangomaner.mangobot.api.MangoGroupMessageApi;
import io.github.mangomaner.mangobot.api.MangoPrivateMessageApi;
import io.github.mangomaner.mangobot.service.BotFilesService;
import io.github.mangomaner.mangobot.service.GroupMessagesService;
import io.github.mangomaner.mangobot.service.PrivateMessagesService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * 负责初始化静态 API 类的管理器
 */
@Component
public class MangoApiManager {

    @Resource
    private GroupMessagesService groupMessagesService;

    @Resource
    private PrivateMessagesService privateMessagesService;

    @Resource
    private BotFilesService botFilesService;

    @PostConstruct
    public void init() {
        // 初始化静态 API
        try {
            // 使用反射或直接访问包级私有方法（需要 api 和 manager 在同包下，或者提供 public setService 但不推荐，或者放在同包）
            // 由于 Api 类在 .api 包，Manager 在 .manager 包，直接调用 package-private 方法不可行。
            // 解决方案：
            // 1. 将 Manager 放在 .api 包 (不太合适)
            // 2. 将 setService 设为 public，但加上 @Deprecated 或注释警告 "Internal use only"
            // 3. 使用反射调用 setService (安全且隐蔽)
            
            initApi(MangoGroupMessageApi.class, "setService", GroupMessagesService.class, groupMessagesService);
            initApi(MangoPrivateMessageApi.class, "setService", PrivateMessagesService.class, privateMessagesService);
            initApi(MangoFileApi.class, "setService", BotFilesService.class, botFilesService);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize Mango APIs", e);
        }
    }

    private void initApi(Class<?> apiClass, String methodName, Class<?> serviceType, Object serviceInstance) throws Exception {
        java.lang.reflect.Method method = apiClass.getDeclaredMethod(methodName, serviceType);
        method.setAccessible(true);
        method.invoke(null, serviceInstance);
    }
}
