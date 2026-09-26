package io.github.mangomaner.mangobot.adapter.onebot.utils;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 聊天信封的唯一模板：{@code [时间] 名字(qq号): 正文}。
 *
 * <p>为什么抽出来：这个格式原来只在 {@code QqReplyHandler} 里私有实现，而测评要喂同一批格式的信封
 * 给模型——两处各写一遍，改了一处忘了另一处，测评就会拿一种格式去衡量另一种格式的产物，结论全是假的。
 * 时间格式同理，见 {@link #formatTime(long)}。
 *
 * <p>时间不在这里<b>拼</b>进模板：生产链路传的是完整时间戳（{@code yyyy-MM-dd HH:mm:ss}），
 * 而用例里只有 {@code HH:mm}（原样取自人工整理的语料），那一层由调用方决定。这里只负责括号与冒号。
 * {@code qq} 允许为 null（用例里通常没有真实号码），那时不写 {@code (qq号)} 段。
 */
public final class ChatEnvelope {

    /** 生产链路的时间格式：完整时间戳到秒。 */
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private ChatEnvelope() {
    }

    /**
     * 把 OneBot 事件的 epoch 秒格式化成信封里的时间文本。
     *
     * <p>和模板一样放在这个类里、而不是各调用方各写一遍：模拟群回放要喂"看起来和生产一模一样"的信封，
     * 两处格式一旦漂移，测评衡量的就不是生产的产物了。回放的墙钟是压缩的，但信封时间不是——它由帧里的
     * {@code time} 决定，所以对模型来说时间线仍然和用例写的一致。
     */
    public static String formatTime(long epochSeconds) {
        return TIME_FORMAT.format(Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.systemDefault()));
    }

    /**
     * 拼一行信封。
     *
     * @param time      已格式化好的时间文本
     * @param name      发送者昵称；空白时由调用方先兜底成别的东西（生产上是 QQ 号）
     * @param qq        QQ 号；null 时不写括号段
     * @param body      正文（已渲染成模型字面格式，图片形如 {@code [图片#id: 描述]}）
     * @param messageId 本条的消息 id；非 null 时在行尾补一个 {@code [消息#id]}。为 null 则不写
     */
    public static String render(String time, String name, Long qq, String body, Long messageId) {
        StringBuilder sb = new StringBuilder();
        sb.append('[').append(time == null ? "" : time).append("] ");
        sb.append(name == null || name.isBlank() ? "?" : name);
        if (qq != null) {
            sb.append('(').append(qq).append(')');
        }
        sb.append(": ").append(body == null ? "" : body);
        if (messageId != null) {
            sb.append(" [消息#").append(messageId).append(']');
        }
        return sb.toString();
    }

    /**
     * 不带消息 id 的一行信封。
     *
     * <p><b>行尾那个 {@code [消息#id]} 是"想引用某句"的唯一凭据</b>：模型要引用哪条，就得把这个数字
     * 交给 {@code send_reply} 的 {@code reply_to}。没有它，模型只能引用"刚喂进来的最后一条"——
     * 而实测真人引用紧邻上一条只占 9.7%，绝大多数引用指向的是已经被刷上去好几句的某条。
     * 所以生产链路一律走上面那个五参重载；这个重载留给"手上确实没有 id"的场合（测试、离线构造）。
     */
    public static String render(String time, String name, Long qq, String body) {
        return render(time, name, qq, body, null);
    }
}
