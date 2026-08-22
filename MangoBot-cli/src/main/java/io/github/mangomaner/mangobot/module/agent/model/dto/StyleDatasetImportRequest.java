package io.github.mangomaner.mangobot.module.agent.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 从会话消息多选导入风格数据集请求。
 *
 * <p>chat_messages 表不存发送者昵称，故由前端按选中消息逐条携带 senderName 传入，
 * 后端按发送者自动归并生成角色（统一为普通角色）。
 */
@Data
@Schema(description = "从会话导入风格数据集请求")
public class StyleDatasetImportRequest {

    @Schema(description = "数据集名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "来源：group / private", requiredMode = Schema.RequiredMode.REQUIRED)
    private String source;

    @Schema(description = "来源会话 id（导入时记录）")
    private Long sourceSessionId;

    @Schema(description = "选中消息列表（按发送者自动生成角色）")
    private List<ImportMessage> messages;

    @Data
    @Schema(description = "导入的单条消息")
    public static class ImportMessage {
        @Schema(description = "发送者昵称（归并角色的依据）")
        private String senderName;
        @Schema(description = "消息文本")
        private String text;
    }
}
