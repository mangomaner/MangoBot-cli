package io.github.mangomaner.mangobot.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "根据配置键修改配置请求")
public class UpdateConfigByKeyRequest {

    @Schema(description = "配置键")
    @NotBlank(message = "配置键不能为空")
    private String configKey;

    @Schema(description = "配置值")
    @NotBlank(message = "配置值不能为空")
    private String configValue;
}
