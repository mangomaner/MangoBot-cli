package io.github.mangomaner.mangobot.model.dto.message;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "查询最新消息请求")
public class QueryLatestMessagesRequest {

    @Schema(description = "Bot ID")
    @NotNull(message = "Bot ID不能为空")
    private Integer botId;

    @Schema(description = "群组ID或好友ID")
    @NotNull(message = "群组ID或好友ID不能为空")
    private Integer targetId;
}
