package org.mango.mangobot.messageHandler;

import org.mango.mangobot.annotation.QQ.method.AtMessage;
import org.mango.mangobot.annotation.QQ.method.DefaultMessage;
import org.mango.mangobot.annotation.QQ.method.PokeMessage;
import org.mango.mangobot.annotation.QQ.method.TextMessage;
import org.mango.mangobot.annotation.QQ.parameter.*;
import org.mango.mangobot.model.dto.handler.ChatMessageDTO;
import org.springframework.stereotype.Component;

/**
 * 该方法已弃用！改为插件开发！！！
 */

/**
 *
 * 相关注解代码在 org/mangobot/annotation/QQ 定义，请勿混淆 方法注解 和 参数注解
 *  规则：
 *      1. 一次只会有一个方法被调用
 *      2. 精准匹配，收到消息的类型需要和你注解标注的类型 完全相同
 *      3. 按需取用 参数注解 ，参数名称可以随意取，只要标注对应注解即可
 *  建议：
 *      1. 请勿将 实际不会出现的消息组合 注解到同一个方法上，这会导致该方法永远不会执行
 */
@Component
public class GroupMessageHandler {

    /**
     * 文本、at、图片、回复 消息组合事件（单独事件，请勿和其他进行组合，优先级最低，相当于保底）
     */
    @DefaultMessage
    public void handleCombinationMessage(ChatMessageDTO chatMessageDTO){

    }

    /**
     * 处理文本消息（如果只有文本消息，则匹配该方法）
     */
    @TextMessage
    public void handleTextMessage(ChatMessageDTO chatMessageDTO){

    }

    /**
     * 处理文本和At的组合消息（如果同时有 文本和At 的消息，则匹配该方法）
     * 值得注意的是，QQ的At消息后默认会存在一个空格，该空格会被识别为Text，因此一般不建议单独使用 @AtMessage
     */
    @TextMessage
    @AtMessage
    public void handleTextWithAtMessage(ChatMessageDTO chatMessageDTO){

    }

    /**
     * 戳一戳事件（单独事件，请勿和其他进行组合）
     */
    @PokeMessage
    public void handlePoke(ChatMessageDTO chatMessageDTO) {

    }
}