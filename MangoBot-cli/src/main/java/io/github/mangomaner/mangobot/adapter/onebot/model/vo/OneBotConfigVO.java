package io.github.mangomaner.mangobot.adapter.onebot.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "OneBot 配置视图对象")
public class OneBotConfigVO {

    @Schema(description = "配置ID")
    private Long id;

    @Schema(description = "配置名称")
    private String name;

    @Schema(description = "WebSocket 服务器地址（ws://host:port/path）")
    private String url;

    @Schema(description = "访问令牌")
    private String token;

    @Schema(description = "是否启用：0-禁用, 1-启用")
    private Integer enabled;

    @Schema(description = "连接状态：0-未启动, 1-运行中, 2-已停止, 3-错误")
    private Integer connectionStatus;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "创建时间")
    private Long createdAt;

    @Schema(description = "更新时间")
    private Long updatedAt;
}
