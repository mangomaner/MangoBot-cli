package io.github.mangomaner.mangobot.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Ai测试信息返回")
public class AiTestResultVo {
    @Schema(description = "测试是否成功")
    private Boolean success;

    @Schema(description = "返回成功信息/错误原因")
    private String result;

}
