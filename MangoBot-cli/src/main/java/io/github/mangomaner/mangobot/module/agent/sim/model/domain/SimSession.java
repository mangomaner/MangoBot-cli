package io.github.mangomaner.mangobot.module.agent.sim.model.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 一次模拟对话（一组）。
 *
 * <p><b>只存"这场对话本身 + 对它的打分"</b>，不存用例、不存指标、不存通过与否。模拟是手工驱动的
 * 一次性对练：外部 agent 拉一个虚拟群、按真实语料改编的剧本一句句喂，bot 照生产链路回，
 * 然后由一个独立的 judge 会话按几个角度打分。要的是"像不像群里的人"，不是回归断言。
 *
 * <p>{@code scores} 与 {@code verdict} 都是 judge 的产物：前者是各角度的分与原文证据（JSON），
 * 后者是一句话总评。分列是为了列表页不必反序列化 {@code scores}。
 */
@TableName(value = "sim_session")
@Data
public class SimSession implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /** 人读的标题，例如"面试失败后冷场"。 */
    @TableField(value = "title")
    private String title;

    /** group / private。 */
    @TableField(value = "source")
    private String source;

    /** 被测虚拟 bot 的号（保留号段，见 {@code SimBotProfile}）。 */
    @TableField(value = "bot_id")
    private String botId;

    /** 虚拟群号 / 用户号（保留号段）。 */
    @TableField(value = "chat_id")
    private String chatId;

    /** 这轮测的是哪版人格。手写标签——换一版再跑时，列表里靠它区分。 */
    @TableField(value = "persona_label")
    private String personaLabel;

    /** 剧本的语料出处（只记来源，不存原文）。 */
    @TableField(value = "seed_ref")
    private String seedRef;

    /** running（对练中）/ scoring（评判中）/ done / failed。 */
    @TableField(value = "status")
    private String status;

    /** 各角度的分与原文证据，JSON 数组。 */
    @TableField(value = "scores")
    private String scores;

    /** judge 的一句话总评。 */
    @TableField(value = "verdict")
    private String verdict;

    @TableField(value = "create_time")
    private Date createTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
