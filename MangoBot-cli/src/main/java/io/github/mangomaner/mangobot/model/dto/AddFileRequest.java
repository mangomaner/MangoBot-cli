package io.github.mangomaner.mangobot.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "添加文件请求")
public class AddFileRequest {

    @Schema(description = "文件类型")
    @NotBlank(message = "文件类型不能为空")
    private String fileType;

    @Schema(description = "文件ID")
    @NotBlank(message = "文件ID不能为空")
    private String fileId;

    @Schema(description = "文件URL")
    private String url;

    @Schema(description = "文件相对路径")
    private String filePath;

    @Schema(description = "图片子类型")
    private Integer imageSubType;

    @Schema(description = "文件大小")
    private Integer fileSize;

    @Schema(description = "文件描述")
    private String description;
}
