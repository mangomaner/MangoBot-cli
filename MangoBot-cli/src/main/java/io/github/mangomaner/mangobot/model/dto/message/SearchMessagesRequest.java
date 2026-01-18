package io.github.mangomaner.mangobot.model.dto.message;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "搜索消息请求")
public class SearchMessagesRequest {

    @Schema(description = "Bot ID")
    @NotNull(message = "Bot ID不能为空")
    private Long botId;

    @Schema(description = "群组ID或好友ID")
    @NotNull(message = "群组ID或好友ID不能为空")
    private Long targetId;

    @Schema(description = "搜索关键词")
    @NotBlank(message = "搜索关键词不能为空")
    private String keyword;
}
