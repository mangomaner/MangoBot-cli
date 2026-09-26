package io.github.mangomaner.mangobot.module.agent.sim.model.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 模拟对话里的一条消息。
 *
 * <p>两个方向都记在这一张表里，用 {@code role} 区分：{@code peer} 是剧本里由外部 agent 喂进去的
 * 群友发言，{@code bot} 是被测 bot 真正发出来的（从出站帧还原，是既成事实）。
 * 按 {@code seq} 排出时间顺序，就是这场对话的完整转录。
 */
@TableName(value = "sim_message")
@Data
public class SimMessage implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @TableField(value = "session_id")
    private Integer sessionId;

    /** 会话内的顺序号，从 1 开始。 */
    @TableField(value = "seq")
    private Integer seq;

    /** 显示名（信封里那个昵称）。 */
    @TableField(value = "speaker")
    private String speaker;

    /** 发送者号；bot 的发言是虚拟 bot 号，群友的是保留段假号。 */
    @TableField(value = "sender_id")
    private String senderId;

    /** {@code peer}（剧本喂进去的）/ {@code bot}（被测 bot 发出来的）。 */
    @TableField(value = "role")
    private String role;

    /** {@code text} / {@code image} / {@code meme}。 */
    @TableField(value = "kind")
    private String kind;

    /** 正文；图片/表情包是 fileId（表情包的关键词在路上不存在，见 {@code SimOneBotClient}）。 */
    @TableField(value = "content")
    private String content;

    /** bot 这条引用了哪条消息；没引用为 null。（群友那边是剧本喂的，不涉及。） */
    @TableField(value = "reply_to")
    private String replyTo;

    /** 会话内相对时间（毫秒），从开场算起。 */
    @TableField(value = "at_ms")
    private Long atMs;

    @TableField(value = "create_time")
    private Date createTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
