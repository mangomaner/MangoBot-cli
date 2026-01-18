package io.github.mangomaner.mangobot.model.dto.message;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "更新消息请求")
public class UpdateMessageRequest {

    @Schema(description = "消息ID")
    @NotNull(message = "消息ID不能为空")
    private Long id;

    @Schema(description = "消息段")
    private String messageSegments;

    @Schema(description = "解析后的消息")
    private String parseMessage;
}
