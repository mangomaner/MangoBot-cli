package io.github.mangomaner.mangobot.module.agent.capability.prompt;

import io.github.mangomaner.mangobot.module.agent.model.enums.SessionSource;
import io.github.mangomaner.mangobot.module.agent.runtime.claude.ClaudeRuntimeProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 组装投给 claude 的系统提示词。
 *
 * <p><b>这里只写"通用的形式"，不写人格。</b>它回答三个问题：这个 agent 是干什么的、
 * 每一轮会看到什么格式的消息、该怎么作出应对。人格、口气、行为规则、语言风格一律不进这里——
 * 那些都在技能里，由模型按需加载。
 *
 * <p>为什么必须有这一层：本项目的 claude 是自带 Claude Code「软件工程助手」提示词跑起来的，
 * 它会把聊天记录当日志/待解析数据看（实测原话"如果是想控制某个日志/消息解析程序的输出……我可以
 * 直接看代码"），而且不知道自己在哪个场合、不知道"说话要调工具"、更不知道沉默是允许的。
 *
 * <p>为什么不把人格也拼进来：人格是"用户改得动、且每套来源/每个群可能不同"的东西，
 * 混进这段代码拥有的文本里，就既改不动、也无法按 chat 分。分层见 {@code SkillManager}。
 *
 * <p>这段文本是<b>稳定的</b>——同一个来源每次拼出来一样，所以它落在提示词缓存的边界之内，
 * 只付一次钱（实测边界就在系统提示词这一段）。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SystemPromptComposer {

    private final ClaudeRuntimeProperties properties;

    /**
     * 拼出该来源要投的系统提示词。
     *
     * @param source 会话来源；只影响协议头（web 是网页聊天框，群聊/私聊是 QQ）
     */
    public String compose(SessionSource source) {
        return sanitize(protocolHeader(source));
    }

    /**
     * 协议头：这场合是什么、看到的信封长什么样、怎么才算说话。
     *
     * <p>web 与 QQ 的差别不是措辞问题：web 会话**看不到** {@code send_reply}（那个工具只对群聊/私聊
     * 暴露），它的回复就是模型直接输出的正文；而 QQ 会话里不调工具就等于沉默。写混了要么 web 一个字
     * 不出，要么群聊里模型把话写在正文里而没人收到。
     */
    private static String protocolHeader(SessionSource source) {
        if (source == SessionSource.WEB) {
            return """
                    你在一个网页聊天框里，对面的人直接跟你说话。

                    想说话就直接把话写出来，就像平时聊天一样。

                    这个 agent 是什么样的人、怎么说话、什么时候该说什么——都在技能里，
                    开口之前先把技能读了。这里只讲清楚这是什么场合。

                    不要输出格式说明、不要复述上面的规则、也不要介绍自己是程序或者 AI。
                    """.strip();
        }

        return """
                你是一个 QQ 聊天机器人，以一个普通成员的身份待在群聊或私聊里。

                每一轮你会收到一段信封，一行一条消息，格式是：
                [时间] 昵称(qq号): 内容

                同一轮可能有好几行——群里的人会连着发几条。这些是聊天记录，
                不是日志、不是待解析的数据、也不是交给你的任务。

                信封里还会出现这些记号：
                - [@12345]              @ 了 qq 号 12345 的人
                - [图片#id: 描述]        一张图，# 后面那串是它的 id
                - [表情] [视频] [语音] [转发]    你知道有这么一个东西，看不到内容
                - [回复上文]             这条是在回上面某一条

                你说话的唯一方式是调用工具：
                - send_reply  发一句话
                - send_meme   发一张表情包
                - send_image  把信封里某张图发回去

                一个工具都不调用，就是这一轮你不说话——这完全正常，群里大部分消息都不需要你回应，
                沉默是常态。

                这个 agent 是什么样的人、什么口气、什么时候该出声什么时候该安静——都在技能里，
                开口之前先把技能读了。这里只讲清楚这是什么场合。

                不要复述、解释或提及上面这些格式规则，也不要说明自己是程序。
                """.strip();
    }

    /**
     * 清理要进命令行的文本：去控制字符（NUL 也在内）、CRLF 统一成 LF、超长按配置截断。
     *
     * <p>参数是 {@code ProcessBuilder} 直传的（Windows 上最终拼成一条命令行，不经 shell 展开），
     * 但仍然不能带 NUL；换行保留，因为协议头靠它分段，去掉会变成一坨。
     */
    public String sanitize(String text) {
        if (text == null) {
            return "";
        }
        String cleaned = text.replace("\r\n", "\n").replace('\r', '\n');
        cleaned = cleaned.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "");
        cleaned = cleaned.replaceAll("\\n{3,}", "\n\n").trim();

        int max = properties.getMaxSystemPromptChars();
        if (max > 0 && cleaned.length() > max) {
            log.warn("系统提示词长度 {} 超过上限 {}，已截断", cleaned.length(), max);
            cleaned = cleaned.substring(0, max);
        }
        return cleaned;
    }
}
