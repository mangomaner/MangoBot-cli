package io.github.mangomaner.mangobot.model.dto.message;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "查询发送者最新消息请求")
public class QueryMessagesBySenderRequest {

    @Schema(description = "Bot ID")
    @NotNull(message = "Bot ID不能为空")
    private Long botId;

    @Schema(description = "发送者ID")
    @NotNull(message = "发送者ID不能为空")
    private Long senderId;
}
