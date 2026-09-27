package io.github.mangomaner.mangobot.module.agent.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 指派（或取消）某场景的人格请求。
 *
 * <p>场景互斥在「人格页」上表现为"每种场景只允许选一个人格"，落到数据层由
 * {@code SkillManager.assignPersonaSource} 保证：某来源的新目标挂上、旧目标摘掉。
 */
@Data
@Schema(description = "指派/取消场景人格请求")
public class AssignPersonaRequest {

    @NotBlank(message = "来源不能为空")
    @Schema(description = "来源 key：web / group / private", requiredMode = Schema.RequiredMode.REQUIRED)
    private String source;

    @Schema(description = "要指派的人格 skill 路径；传 null 表示取消该来源的人格")
    private String personaPath;
}