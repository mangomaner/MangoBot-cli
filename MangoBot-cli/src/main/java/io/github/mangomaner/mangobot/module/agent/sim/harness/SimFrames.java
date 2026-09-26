package io.github.mangomaner.mangobot.module.agent.sim.harness;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 造"入站消息帧"：让模拟群友的发言看起来就是从 QQ 来的。
 *
 * <p>形状对齐真实 NapCat 的群消息上报（{@code post_type=message} / {@code message_type=group} /
 * {@code message} 为段数组 / 带 {@code sender}）。应用侧一个字都不用改——这正是"OneBot WS 是天然接缝"
 * 的兑现：伪造一个 NapCat 不需要改应用一行代码。
 *
 * <p>{@code text} 用的是<b>模型字面形式</b>（{@code [表情]} / {@code [@123]} / {@code [图片#id: 描述]}），
 * 原样放进一个 text 段就能得到与生产一模一样的信封（{@code MessageLexer} 对 text 段是原样输出）。
 */
public final class SimFrames {

    private SimFrames() {
    }

    /**
     * 一条带图的群消息：图（+ 可选的一段文字）。
     *
     * <p>{@code url} 必须是应用能下载的地址——它走 {@code FileUtils.downloadFile}（HttpClient），
     * 所以指向 {@link SimMediaServer} 那个小服务。这样图片会真的落进 {@code bot_files}，
     * 再由 {@code ChatTurnRunner} 读字节当图片块送进模型——与生产同形状。
     *
     * @param subType 1/11 是表情包（存 data/meme），其余当普通图（存 data/image）
     */
    public static String groupMessageWithImage(long selfId, long groupId, long memberQq, String nickname,
                                               String text, String fileId, String url, int subType,
                                               int messageId, long epochSeconds) {
        ObjectNode root = baseFrame(selfId, groupId, memberQq, nickname, text, messageId, epochSeconds);
        ArrayNode message = (ArrayNode) root.get("message");

        ObjectNode image = message.insertObject(0);
        image.put("type", "image");
        ObjectNode data = image.putObject("data");
        data.put("file", fileId);
        data.put("url", url);
        data.put("sub_type", subType);
        return root.toString();
    }

    /** 一条群消息。{@code epochSeconds} 是帧上自报的时间，信封里的 {@code [HH:mm]} 由它来。 */
    public static String groupMessage(long selfId, long groupId, long memberQq, String nickname,
                                      String text, int messageId, long epochSeconds) {
        ObjectNode root = baseFrame(selfId, groupId, memberQq, nickname, text, messageId, epochSeconds);
        ArrayNode message = (ArrayNode) root.get("message");
        ObjectNode segment = message.addObject();
        segment.put("type", "text");
        segment.putObject("data").put("text", text == null ? "" : text);
        return root.toString();
    }

    /** 帧的公共部分（不含 message 段）。 */
    private static ObjectNode baseFrame(long selfId, long groupId, long memberQq, String nickname,
                                        String text, int messageId, long epochSeconds) {
        ObjectNode root = SimJson.MAPPER.createObjectNode();
        root.put("post_type", "message");
        root.put("message_type", "group");
        root.put("sub_type", "normal");
        root.put("self_id", selfId);
        root.put("user_id", memberQq);
        root.put("group_id", groupId);
        root.put("message_id", messageId);
        root.put("real_id", messageId);
        root.put("time", epochSeconds);
        root.put("raw_message", text == null ? "" : text);
        root.put("font", 0);
        root.put("message_format", "array");

        root.putArray("message");

        ObjectNode sender = root.putObject("sender");
        sender.put("user_id", memberQq);
        sender.put("nickname", nickname);
        sender.put("card", nickname);
        sender.put("role", "member");
        return root;
    }
}
