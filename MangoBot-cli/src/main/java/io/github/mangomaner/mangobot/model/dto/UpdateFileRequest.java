package io.github.mangomaner.mangobot.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "更新文件请求")
public class UpdateFileRequest {

    @Schema(description = "文件ID")
    @NotNull(message = "文件ID不能为空")
    private Integer id;

    @Schema(description = "文件类型")
    private String fileType;

    @Schema(description = "文件URL")
    private String url;

    @Schema(description = "文件相对路径")
    private String filePath;

    @Schema(description = "文件子类型")
    private Integer subType;

    @Schema(description = "文件大小")
    private Integer fileSize;

    @Schema(description = "文件描述")
    private String description;
}
