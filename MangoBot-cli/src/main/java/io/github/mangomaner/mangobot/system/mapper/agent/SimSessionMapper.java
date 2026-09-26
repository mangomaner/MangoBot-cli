package io.github.mangomaner.mangobot.system.mapper.agent;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.mangomaner.mangobot.module.agent.sim.model.domain.SimSession;

/**
 * 针对表【sim_session】的数据库操作 Mapper。
 *
 * <p>表由 {@code SimSchema} 兜底建（本项目没有迁移机制，只在 DB 文件不存在时跑 {@code schema.sql}，
 * 老库升不上来），所以 {@code schema.sql} 与那句 {@code CREATE TABLE IF NOT EXISTS} 必须保持一致。
 */
public interface SimSessionMapper extends BaseMapper<SimSession> {
}
