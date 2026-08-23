package io.github.mangomaner.mangobot.message_handler.response.tools;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.github.mangomaner.mangobot.adapter.onebot.handler.outbound.build_sending_message.OneBotSendingMessage;
import io.github.mangomaner.mangobot.annotation.MangoTool;
import io.github.mangomaner.mangobot.api.MangoFileApi;
import io.github.mangomaner.mangobot.api.MangoOneBotApi;
import io.github.mangomaner.mangobot.api.context.ChatContext;
import io.github.mangomaner.mangobot.api.context.state.ToolExecuteState;
import io.github.mangomaner.mangobot.module.agent.model.enums.SessionSource;
import io.github.mangomaner.mangobot.module.file.model.domain.BotFiles;

import java.util.List;
import java.util.Random;

@MangoTool(name = "GroupMessageSend", description = "发送群聊消息", category = "Group")
public class GroupMessageSendTool {

    /** 同一条消息内连续调用发送工具超过该次数后开始提醒（不强制停止） */
    private static final int REPLY_TOOL_WARN_THRESHOLD = 5;

    private final Random random = new Random();

    @Tool(description = "发送群聊文本消息，延续当前对话（默认方式）；文本内可用 [@QQ号] @某人")
    public String sendGroupMessage(
            @ToolParam(name = "message", description = "要发送的群聊消息内容，文本中的 [@QQ号] 会 @ 对应 QQ")
            String message,
            ChatContext context) {
        if (context.getSource() != null && context.getSource() != SessionSource.GROUP) {
            return "该工具仅用于群聊会话，请改用私聊发送工具";
        }
        OneBotSendingMessage sendMessage = MessageSendSupport.textMessage(message, null);
        MangoOneBotApi.sendGroupMsg(Long.parseLong(context.getBotId()), Long.parseLong(context.getChatId()), sendMessage);
        return withCount(context, "sendGroupMessage");
    }

    @Tool(description = "回复某条指定的群消息（仅当要回复的那条距当前消息较远时使用）；文本内可用 [@QQ号] @某人")
    public String sendGroupReply(
            @ToolParam(name = "message", description = "要发送的群聊消息内容，文本中的 [@QQ号] 会 @ 对应 QQ")
            String message,
            @ToolParam(name = "replyToMessageId", description = "要回复的群消息 id，取信封里对应消息的 message_id")
            Long replyToMessageId,
            ChatContext context) {
        if (context.getSource() != null && context.getSource() != SessionSource.GROUP) {
            return "该工具仅用于群聊会话，请改用私聊发送工具";
        }
        OneBotSendingMessage sendMessage = MessageSendSupport.textMessage(message, replyToMessageId);
        MangoOneBotApi.sendGroupMsg(Long.parseLong(context.getBotId()), Long.parseLong(context.getChatId()), sendMessage);
        return withCount(context, "sendGroupMessage");
    }

    @Tool(description = "获取可发送的表情包列表，返回表情包的 fileId 和描述信息，不占用回复次数")
    public String getMemeImages() {
        List<BotFiles> memeFiles = MangoFileApi.getFilesByType("meme");

        if (memeFiles == null || memeFiles.isEmpty()) {
            return "当前没有可用的表情包";
        }

        java.util.Collections.shuffle(memeFiles, random);
        int count = Math.min(3, memeFiles.size());

        StringBuilder result = new StringBuilder("推荐的表情包：\n");
        for (int i = 0; i < count; i++) {
            BotFiles file = memeFiles.get(i);
            result.append(String.format("%d. fileId: %s, 描述: %s\n",
                    i + 1,
                    file.getFileId(),
                    file.getDescription() != null ? file.getDescription() : "无描述"));
        }
        result.append("\n使用 sendGroupImage 工具发送表情包，传入对应的 fileId");

        return result.toString();
    }

    @Tool(description = "发送图片到群聊，延续当前对话（默认方式）；fileId 从 getMemeImages 获取，或取上下文里图片的 fileId")
    public String sendGroupImage(
            @ToolParam(name = "fileId", description = "要发送的图片 fileId")
            String fileId,
            ChatContext context) {
        if (context.getSource() != null && context.getSource() != SessionSource.GROUP) {
            return "该工具仅用于群聊会话，请改用私聊发送工具";
        }
        BotFiles file = MangoFileApi.getFileByFileId(fileId);

        if (file == null) {
            return "图片不存在或 fileId 无效，请先调用 getMemeImages 获取可用表情包，或使用上下文里图片的 fileId";
        }
        if (file.getFilePath() == null || file.getFilePath().isEmpty()) {
            return "图片文件路径无效";
        }

        OneBotSendingMessage sendMessage;
        try {
            sendMessage = MessageSendSupport.imageMessage(file, null);
        } catch (IllegalStateException e) {
            return e.getMessage();
        }
        MangoOneBotApi.sendGroupMsg(Long.parseLong(context.getBotId()), Long.parseLong(context.getChatId()), sendMessage);
        return withCount(context, "sendGroupImage");
    }

    @Tool(description = "发送图片并回复某条指定的群消息（仅当要回复的那条距当前消息较远时使用）；fileId 从 getMemeImages 获取，或取上下文里图片的 fileId")
    public String sendGroupReplyImage(
            @ToolParam(name = "fileId", description = "要发送的图片 fileId")
            String fileId,
            @ToolParam(name = "replyToMessageId", description = "要回复的群消息 id，取信封里对应消息的 message_id")
            Long replyToMessageId,
            ChatContext context) {
        if (context.getSource() != null && context.getSource() != SessionSource.GROUP) {
            return "该工具仅用于群聊会话，请改用私聊发送工具";
        }
        BotFiles file = MangoFileApi.getFileByFileId(fileId);

        if (file == null) {
            return "图片不存在或 fileId 无效，请先调用 getMemeImages 获取可用表情包，或使用上下文里图片的 fileId";
        }
        if (file.getFilePath() == null || file.getFilePath().isEmpty()) {
            return "图片文件路径无效";
        }

        OneBotSendingMessage sendMessage;
        try {
            sendMessage = MessageSendSupport.imageMessage(file, replyToMessageId);
        } catch (IllegalStateException e) {
            return e.getMessage();
        }
        MangoOneBotApi.sendGroupMsg(Long.parseLong(context.getBotId()), Long.parseLong(context.getChatId()), sendMessage);
        return withCount(context, "sendGroupImage");
    }

    @Tool(description = "保持沉默，本次不发送任何回复（群聊中不满足回复条件时使用）")
    public String staySilent(ChatContext context) {
        ToolExecuteState state = context.getToolExecuteState();
        if (state.getToolExecuteCount("staySilent") > 0) {
            return "已保持沉默，无需重复调用";
        }
        state.addToolExecuteCount("staySilent");
        return "已保持沉默，本次不发送任何回复";
    }

    private String withCount(ChatContext context, String toolName) {
        ToolExecuteState state = context.getToolExecuteState();
        int sentCount = state.getToolExecuteCount(toolName);
        state.addToolExecuteCount(toolName);
        if (sentCount >= REPLY_TOOL_WARN_THRESHOLD) {
            return "发送成功。注意：你已连续调用发送工具多次，可能过于频繁；本条消息仍会发送，请把要说的话尽快说完，然后结束本轮回复";
        }
        return "发送成功";
    }
}
