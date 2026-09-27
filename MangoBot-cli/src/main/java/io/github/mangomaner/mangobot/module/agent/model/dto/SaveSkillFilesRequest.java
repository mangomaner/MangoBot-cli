package io.github.mangomaner.mangobot.module.agent.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.Map;

/**
 * 保存 Skill 目录全量快照请求。
 *
 * <p>前端在本地完成全部文件操作（新建/改名/删除/编辑内容）后，把整份"相对路径 → 内容"一次发过来；
 * 后端按快照重建目录，天然覆盖改名与删除（旧 key 消失即旧文件被清）。
 *
 * <p>键必须以 "SKILL.md" 开头保留该文件（它是 skill 的元数据文件，不能删）。
 */
@Data
@Schema(description = "保存 Skill 目录全量快照请求")
public class SaveSkillFilesRequest {

    @NotEmpty(message = "文件快照不能为空")
    @Schema(description = "skill 目录内相对路径 → 文件内容", requiredMode = Schema.RequiredMode.REQUIRED)
    private Map<String, String> files;
}