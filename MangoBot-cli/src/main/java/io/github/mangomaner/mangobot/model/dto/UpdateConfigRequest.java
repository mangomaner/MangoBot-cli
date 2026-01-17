package io.github.mangomaner.mangobot.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "修改配置请求")
public class UpdateConfigRequest {

    @Schema(description = "配置ID")
    @NotNull(message = "配置ID不能为空")
    private Integer id;

    @Schema(description = "配置值")
    @NotBlank(message = "配置值不能为空")
    private String configValue;
}
