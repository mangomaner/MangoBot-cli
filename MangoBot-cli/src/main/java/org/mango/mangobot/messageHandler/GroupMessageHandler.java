package org.mango.mangobot.messageHandler;

import jakarta.annotation.Priority;
import lombok.extern.slf4j.Slf4j;
import org.mango.mangobot.annotation.QQ.method.AtMessage;
import org.mango.mangobot.annotation.QQ.method.DefaultMessage;
import org.mango.mangobot.annotation.QQ.method.PokeMessage;
import org.mango.mangobot.annotation.QQ.method.TextMessage;
import org.mango.mangobot.model.dto.handler.ChatMessageDTO;
import org.springframework.stereotype.Component;

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
@Slf4j
public class GroupMessageHandler {

    /**
     * 文本、at、图片、回复 消息组合事件（单独事件，请勿和其他进行组合，优先级最低，相当于保底）
     */
    @DefaultMessage
    @Priority(Integer.MAX_VALUE) // 设置为最低优先级，优先使用插件的处理方法
    public void handleCombinationMessage(ChatMessageDTO chatMessageDTO){
        System.out.println("收到组合消息:");
    }

    /**
     * 处理文本消息（如果只有文本消息，则匹配该方法）
     */
    @TextMessage
    @Priority(Integer.MAX_VALUE) // 设置为最低优先级，优先使用插件的处理方法
    public void handleTextMessage(ChatMessageDTO chatMessageDTO){
        log.info("[ExamplePlugin] 收到消息：" + chatMessageDTO.getMessage()
                + " from: " + chatMessageDTO.getUserId()
                + " target:" + chatMessageDTO.getTargetId());
    }

    /**
     * 处理文本和At的组合消息（如果同时有 文本和At 的消息，则匹配该方法）
     * 值得注意的是，QQ的At消息后默认会存在一个空格，该空格会被识别为Text，因此一般不建议单独使用 @AtMessage
     */
    @TextMessage
    @AtMessage
    @Priority(Integer.MAX_VALUE) // 设置为最低优先级，优先使用插件的处理方法
    public void handleTextWithAtMessage(ChatMessageDTO chatMessageDTO){
        System.out.println("收到文本和At的组合消息:");
    }

    /**
     * 戳一戳事件（单独事件，请勿和其他进行组合）
     */
    @PokeMessage
    @Priority(Integer.MAX_VALUE) // 设置为最低优先级，优先使用插件的处理方法
    public void handlePoke(ChatMessageDTO chatMessageDTO) {
        System.out.println("收到戳一戳事件:");
    }
}