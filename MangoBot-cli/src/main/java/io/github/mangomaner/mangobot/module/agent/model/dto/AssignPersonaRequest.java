package io.github.mangomaner.mangobot.module.agent.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 指派（或取消）某作用域的人格请求。
 *
 * <p>作用域由三元组表达，越具体越优先（会话 &gt; bot &gt; 全局）：
 * <ul>
 *   <li>全局：只给 {@code source}</li>
 *   <li>bot 级：给 {@code source} + {@code botId}</li>
 *   <li>会话级：给 {@code source} + {@code botId} + {@code targetId}</li>
 * </ul>
 * 互斥（每个作用域至多一个人格）由 {@code SkillManager.assignPersona} 保证。
 */
@Data
@Schema(description = "指派/取消人格请求")
public class AssignPersonaRequest {

    @NotBlank(message = "来源不能为空")
    @Schema(description = "来源 key：web / group / private", requiredMode = Schema.RequiredMode.REQUIRED)
    private String source;

    @Schema(description = "bot id（QQ 号）；为空表示全局级")
    private String botId;

    @Schema(description = "群号 / 用户号；为空表示非会话级")
    private String targetId;

    @Schema(description = "要指派的人格 skill 路径；传 null 表示取消该作用域的人格")
    private String personaPath;
}
