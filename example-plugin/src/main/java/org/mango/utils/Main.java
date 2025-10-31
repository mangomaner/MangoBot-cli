package org.mango.utils;

import org.apache.ibatis.session.SqlSession;
import org.mango.config.MyBatisConfig;
import org.mango.mapper.ChatMessageMapper;
import org.mango.model.ChatMessage;

public class Main {
    public static void main(String[] args){
        SqlSession session = MyBatisConfig.openSession();
        try {
            ChatMessageMapper mapper = session.getMapper(ChatMessageMapper.class);

            ChatMessage chatMessage = new ChatMessage();
            chatMessage.setGroupId("groupId");
            chatMessage.setUserId("fromUser");
            chatMessage.setTargetId("2002"); // 可选
            chatMessage.setMessage("content");
            chatMessage.setTimestamp(System.currentTimeMillis());

            mapper.insert(chatMessage);
            session.commit();

        } catch (Exception e) {
            session.rollback();
            throw new RuntimeException("插入消息失败", e);
        } finally {
            session.close();
        }
    }
}





//            List<ChatMessage> messages = new ArrayList<>();
//            for (int i = 0; i < 10; i++) {
//                ChatMessage msg = new ChatMessage();
//                msg.setGroupId("group1");
//                msg.setUserId("user" + i);
//                msg.setMessage("这是第 " + i + " 条测试消息");
//                msg.setTimestamp(System.currentTimeMillis());
//                messages.add(msg);
//            }
//
//            int count = mapper.batchInsert(messages); // 返回插入条数
//            session.commit();
//
//            System.out.println("成功插入了 " + count + " 条消息");