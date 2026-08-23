package io.github.mangomaner.mangobot.module.agent.model.dto;

import io.github.mangomaner.mangobot.adapter.onebot.model.segment.OneBotMessageSegment;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 从会话消息多选导入风格数据集请求。
 *
 * <p>chat_messages 表不存发送者昵称，故由前端按选中消息逐条携带 roleName 传入，
 * 后端按发送者自动归并生成角色（统一为普通角色）。消息以结构化 segment 传入，
 * 由后端经 {@code MessageLexer.render(DATASET)} 转成模型字面（[@qq] / [image=file_id]）。
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
        @Schema(description = "发送者昵称/QQ（归并角色的依据）")
        private String roleName;
        @Schema(description = "本消息 message_id（解析回复目标用，可为空）")
        private String messageId;
        @Schema(description = "结构化消息段（仅 text/at/image/reply 会被采用）")
        private List<OneBotMessageSegment> segments;
    }
}
