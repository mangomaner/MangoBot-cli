package io.github.mangomaner.mangobot.message_handler.response.tools;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.github.mangomaner.mangobot.annotation.MangoTool;
import io.github.mangomaner.mangobot.api.MangoFileApi;
import io.github.mangomaner.mangobot.api.MangoOneBotApi;
import io.github.mangomaner.mangobot.api.context.ChatContext;
import io.github.mangomaner.mangobot.api.context.state.ToolExecuteState;
import io.github.mangomaner.mangobot.adapter.onebot.handler.outbound.build_sending_message.OneBotMessageBuilder;
import io.github.mangomaner.mangobot.adapter.onebot.handler.outbound.build_sending_message.OneBotSendingMessage;
import io.github.mangomaner.mangobot.module.agent.model.enums.SessionSource;
import io.github.mangomaner.mangobot.module.file.model.domain.BotFiles;
import io.github.mangomaner.mangobot.utils.FileUtils;

import java.util.List;
import java.util.Random;

@MangoTool(name = "GroupMessageSend", description = "发送群聊消息", category = "Group")
public class GroupMessageSendTool {

    /** 同一条消息内连续调用发送工具超过该次数后开始提醒（不强制停止） */
    private static final int REPLY_TOOL_WARN_THRESHOLD = 5;

    private final Random random = new Random();

    @Tool(description = "发送文本消息")
    public String sendTextMessage(
            @ToolParam(name = "message", description = "要发送的消息内容，请遵循人设和回复规则")
            String message,
            ChatContext context
    ) {
        ToolExecuteState state = context.getToolExecuteState();
        int sentCount = state.getToolExecuteCount("sendTextMessage");
        if (context.getSource() != null && context.getSource() != SessionSource.GROUP) {
            return "该工具仅用于群聊会话，请改用私聊发送工具";
        }
        OneBotSendingMessage sendMessage = OneBotMessageBuilder.create().text(message).build();
        MangoOneBotApi.sendGroupMsg(Long.parseLong(context.getBotId()), Long.parseLong(context.getChatId()), sendMessage);
        state.addToolExecuteCount("sendTextMessage");
        if (sentCount >= REPLY_TOOL_WARN_THRESHOLD) {
            return "发送成功。注意：你已连续调用发送工具多次，可能过于频繁；本条消息仍会发送，请把要说的话尽快说完，然后结束本轮回复";
        }
        return "发送成功";
    }

    @Tool(description = "获取可发送的表情包列表，返回表情包的fileId和描述信息，不占用回复次数")
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
            result.append(String.format("%d. fileName: %s, 描述: %s\n",
                    i + 1,
                    file.getFileId(),
                    file.getDescription() != null ? file.getDescription() : "无描述"));
        }
        result.append("\n使用 sendMemeImage 工具发送表情包，传入对应的 fileName");

        return result.toString();
    }

    @Tool(description = "发送表情包图片到群聊")
    public String sendMemeImage(
            @ToolParam(name = "fileName", description = "要发送的表情包fileName，从getMemeImages获取（格式为xxxxx.xxx）")
            String fileId,
            ChatContext context
    ) {
        if (context.getSource() != null && context.getSource() != SessionSource.GROUP) {
            return "该工具仅用于群聊会话，请改用私聊发送工具";
        }
        BotFiles file = MangoFileApi.getFileByFileId(fileId);

        if (file == null || !"meme".equals(file.getFileType())) {
            return "表情包不存在或fileName无效，请先调用getMemeImages获取可用表情包（格式为xxxxx.xxx）";
        }

        String imagePath = file.getFilePath();
        if (imagePath == null || imagePath.isEmpty()) {
            return "表情包文件路径无效";
        }

        ToolExecuteState state = context.getToolExecuteState();
        int sentCount = state.getToolExecuteCount("sendMemeImage");
        OneBotSendingMessage sendMessage = OneBotMessageBuilder.create()
                .image(FileUtils.getBaseDirectory().normalize().toString().replace('\\', '/') + "/" + imagePath, true)
                .build();

        MangoOneBotApi.sendGroupMsg(Long.parseLong(context.getBotId()), Long.parseLong(context.getChatId()), sendMessage);
        state.addToolExecuteCount("sendMemeImage");
        if (sentCount >= REPLY_TOOL_WARN_THRESHOLD) {
            return "表情包发送成功。注意：你已连续发送多条表情包，可能过于频繁；仍会照常发送，请尽快结束本轮回复";
        }
        return "表情包发送成功";
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
}
