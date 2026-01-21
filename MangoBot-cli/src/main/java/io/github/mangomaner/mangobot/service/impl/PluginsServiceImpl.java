package io.github.mangomaner.mangobot.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import io.github.mangomaner.mangobot.model.domain.Plugins;
import io.github.mangomaner.mangobot.service.PluginsService;
import io.github.mangomaner.mangobot.mapper.PluginsMapper;
import org.springframework.stereotype.Service;

/**
* @author mangoman
* @description 针对表【plugins】的数据库操作Service实现
* @createDate 2026-01-21 11:35:46
*/
@Service
public class PluginsServiceImpl extends ServiceImpl<PluginsMapper, Plugins>
    implements PluginsService {

}




