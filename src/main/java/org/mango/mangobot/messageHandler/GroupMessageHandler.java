package org.mango.mangobot.messageHandler;

import dev.langchain4j.community.model.dashscope.QwenChatModel;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.mango.mangobot.annotation.QQ.method.*;
import org.mango.mangobot.annotation.QQ.parameter.*;
import org.mango.mangobot.service.impl.GroupMessageService;
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
public interface GroupMessageHandler {

    /**
     * 文本、at、图片、回复 消息组合事件（单独事件，请勿和其他进行组合）
     * @param fromUser
     * @param content
     * @param groupId
     * @param imageUrl
     */
    @AtTextImageReplyMessage
    public void handleCombinationMessage(@SenderId String fromUser, // 消息发送者id
                                         @Content String content,   // 消息文字内容
                                         @GroupId String groupId,   // 群id
                                         @ImageURL String imageUrl, // 消息中存在图片的路径
                                         @ReplyContent String replyContent, // 如果存在回复消息，回复的消息id（需配置数据库）
                                         @TargetId String targetId);// 如果存在@消息，@对象的id

    /**
     * 戳一戳事件（单独事件，请勿和其他进行组合）
     * @param fromUser
     */
    @PokeMessage
    public void handlePoke(@SenderId String fromUser,   // 戳一戳发送者id
                           @TargetId String targetUser, // 被戳对象id
                           @GroupId String groupId) ;   // 群id
}