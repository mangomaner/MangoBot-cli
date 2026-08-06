package io.github.mangomaner.mangobot.adapter.onebot.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "更新 OneBot 配置请求")
public class UpdateOneBotConfigRequest {

    @Schema(description = "配置ID", required = true)
    private Long id;

    @Schema(description = "配置名称")
    private String name;

    @Schema(description = "WebSocket 服务器地址（ws://host:port/path）")
    private String url;

    @Schema(description = "访问令牌")
    private String token;

    @Schema(description = "描述")
    private String description;
}
