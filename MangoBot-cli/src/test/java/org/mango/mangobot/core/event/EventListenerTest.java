package org.mango.mangobot.core.event;

import org.junit.jupiter.api.Test;
import org.mango.mangobot.model.onebot.event.MessageEvent;
import org.mango.mangobot.model.onebot.event.message.GroupMessageEvent;
import org.mango.mangobot.model.onebot.event.message.PrivateMessageEvent;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class EventListenerTest {

    private final TestListener testListener = new TestListener();
    
    @Test
    public void testHandleMessageEvent() {
        // 模拟一个群消息事件
        GroupMessageEvent groupEvent = new GroupMessageEvent();
        groupEvent.setUserId(123456L);
        groupEvent.setGroupId(987654L);
        groupEvent.setRawMessage("Hello Group");
        
        // 模拟一个私聊消息事件
        PrivateMessageEvent privateEvent = new PrivateMessageEvent();
        privateEvent.setUserId(123456L);
        privateEvent.setRawMessage("Hello Private");

        // 直接调用监听器方法模拟 Spring 事件分发
        testListener.handleAllMessages(groupEvent);
        testListener.handleAllMessages(privateEvent);
        testListener.handleGroupMessage(groupEvent);

        // 验证通用监听器接收到了两个事件
        assertEquals(2, testListener.allMessages.size());
        assertEquals("Hello Group", testListener.allMessages.get(0).getRawMessage());
        assertTrue(testListener.allMessages.get(0) instanceof GroupMessageEvent);
        
        // 验证专用监听器只接收到了群消息
        assertEquals(1, testListener.groupMessages.size());
        assertEquals(987654L, testListener.groupMessages.get(0).getGroupId());
    }

    // 模拟上层开发者的监听器类
    static class TestListener {
        List<MessageEvent> allMessages = new ArrayList<>();
        List<GroupMessageEvent> groupMessages = new ArrayList<>();

        // 1. 监听所有类型的消息（多态）
        public void handleAllMessages(MessageEvent event) {
            allMessages.add(event);
            System.out.println("收到任意消息: " + event.getRawMessage());
            
            // 可以调用基类方法
            long userId = event.getUserId(); 
        }

        // 2. 只监听群消息
        public void handleGroupMessage(GroupMessageEvent event) {
            groupMessages.add(event);
            System.out.println("收到群消息，群号: " + event.getGroupId());
        }
    }
}
