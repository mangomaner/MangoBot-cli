package org.mango.service;

import org.apache.ibatis.session.SqlSession;
import org.mango.config.MyBatisConfig;
import org.mango.mapper.ChatMessageMapper;
import org.mango.model.ChatMessage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatMessageService {
    // 删除这个成员变量
    //private ChatMessageMapper mapper = MyBatisConfig.openSession().getMapper(ChatMessageMapper.class);
    public ChatMessageService() {}

    public void saveMessage(ChatMessage chatMessage) {
        try (SqlSession session = MyBatisConfig.openSession()) {
            ChatMessageMapper mapper = session.getMapper(ChatMessageMapper.class);
            mapper.insert(chatMessage);
            session.commit();
        } catch (Exception e) {
            throw new RuntimeException("插入消息失败", e);
        }
    }

    /**
     * 查找与当前消息内容相同的其他消息，并统计它们的“下一条消息”
     * 如果某条回复消息出现 ≥ n 次，则返回它
     *
     * @param currentMessage 当前消息
     * @param minCount 最少出现次数
     * @return 高频回复消息列表
     */
    public List<ChatMessage> getFrequentReplies(ChatMessage currentMessage, int minCount) {
        String groupId = currentMessage.getGroupId();
        String message = currentMessage.getMessage();
        String file = currentMessage.getFile();

        System.out.println("【调试】当前群组ID：" + groupId);
        System.out.println("【调试】当前消息内容：" + message + " | 文件：" + file);

        // 每次都使用新的 SqlSession 来获取 Mapper
        try (SqlSession session = MyBatisConfig.openSession()) {
            ChatMessageMapper mapper = session.getMapper(ChatMessageMapper.class);

            // 1. 查询所有内容一致的历史消息
            List<ChatMessage> historyList = mapper.selectByMessageAndFile(groupId, message, file);
            System.out.println("【调试】匹配到的历史消息数量：" + historyList.size());

            // 打印 historyList 内容
            System.out.println("【调试】历史消息列表：");
            for (ChatMessage msg : historyList) {
                System.out.println(" - [时间戳：" + msg.getTimestamp() + "] " + msg.getMessage() + " | 文件：" + msg.getFile());
            }

            Map<String, Integer> replyCountMap = new HashMap<>();
            Map<String, ChatMessage> replyMessageMap = new HashMap<>();

            for (ChatMessage history : historyList) {
                // 2. 找出它的下一条消息（回复）
                ChatMessage nextMsg = mapper.selectNextMessageAfter(groupId, history.getTimestamp());
                if (nextMsg != null) {
                    // 复读信息不予理会
                    if(nextMsg.getMessage().equals(currentMessage.getMessage()) && nextMsg.getFile().equals(currentMessage.getFile())){
                        continue;
                    }
                    // 过长回复不予理会
                    if(nextMsg.getMessage().length() > 10){
                        continue;
                    }
                    System.out.println("【调试】找到下一条消息：" + nextMsg.getMessage() + " | 时间戳：" + nextMsg.getTimestamp());
                    // 使用 message + file 做唯一标识
                    String key = nextMsg.getMessage() + "||" + nextMsg.getFile();
                    replyCountMap.put(key, replyCountMap.getOrDefault(key, 0) + 1);
                    replyMessageMap.put(key, nextMsg);
                } else {
                    System.out.println("【调试】未找到下一条消息（可能是最后一条）");
                }
            }

            // 打印 replyCountMap 统计结果
            System.out.println("【调试】回复消息频率统计：");
            for (Map.Entry<String, Integer> entry : replyCountMap.entrySet()) {
                System.out.println(" - [" + entry.getKey() + "] 出现次数：" + entry.getValue());
            }

            // 3. 收集出现次数 ≥ minCount 的回复
            List<ChatMessage> frequentReplies = new ArrayList<>();
            for (Map.Entry<String, Integer> entry : replyCountMap.entrySet()) {
                if (entry.getValue() >= minCount) {
                    ChatMessage msg = replyMessageMap.get(entry.getKey());
                    frequentReplies.add(msg);
                    System.out.println("【高频回复】\"" + msg.getMessage() + "\" 出现 " + entry.getValue() + " 次");
                }
            }
            return frequentReplies;
        }
    }
}