package io.github.mangomaner.mangobot.system.mapper.agent;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.mangomaner.mangobot.module.agent.sim.model.domain.SimMessage;

/**
 * 针对表【sim_message】的数据库操作 Mapper。
 *
 * <p>表由 {@code SimSchema} 兜底建，与 {@code schema.sql} 的定义必须一致。
 */
public interface SimMessageMapper extends BaseMapper<SimMessage> {
}
