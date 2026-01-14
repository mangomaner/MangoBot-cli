package io.github.mangomaner.mangobot.manager.model.onebot.event;

import io.github.mangomaner.mangobot.manager.event.MangoEventPublisher;
import org.junit.jupiter.api.Test;
import io.github.mangomaner.mangobot.annotation.messageHandler.MangoBotEventListener;
import io.github.mangomaner.mangobot.annotation.PluginPriority;
import io.github.mangomaner.mangobot.model.onebot.event.message.GroupMessageEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = {MangoEventPublisher.class, PriorityTest.TestListener.class})
public class PriorityTest {

    @Autowired
    private MangoEventPublisher publisher;

    @Autowired
    private TestListener listener;

    @Test
    public void testPriorityAndInterruption() {
        // Manually register the test listener methods because they are in a test class/package
        // which might not be covered by the default "org.mango.mangobot" scan or 
        // reflections might behave differently in test context.
        // Actually, MangoEventPublisher scans "org.mango.mangobot".
        // Test classes are usually in the same package structure but under src/test/java.
        // Reflections should pick them up if configured correctly.
        // However, static inner classes might be tricky. 
        // Let's force a scan of the current package just in case.
        publisher.initListeners("org.mango.mangobot");
        
        // Wait, the publisher creates NEW instances of handlers using reflection.
        // It does NOT use the Spring beans.
        // So the 'listener' autowired here is NOT the one used by publisher.
        // We need to verify against the instance managed by publisher, or change design to support Spring beans if available.
        
        // Given the requirement "don't rely on Spring", the publisher manages its own instances.
        // So we cannot easily access the internal state of the listener instance created by publisher
        // unless we expose it or make the logs static.
        
        GroupMessageEvent event = new GroupMessageEvent();
        event.setRawMessage("Test");

        publisher.publish(event);
        
        // Access static logs
        List<String> logs = TestListener.staticLogs;
        
        assertEquals(2, logs.size());
        assertEquals("HighPriority", logs.get(0));
        assertEquals("MediumPriority", logs.get(1));
    }

    public static class TestListener {
        public static List<String> staticLogs = new ArrayList<>();

        public TestListener() {
            staticLogs.clear();
        }

        @MangoBotEventListener
        @PluginPriority(1) // Highest
        public boolean onHighPriority(GroupMessageEvent event) {
            staticLogs.add("HighPriority");
            return true; // Continue
        }

        @MangoBotEventListener
        @PluginPriority(5) // Medium
        public boolean onMediumPriority(GroupMessageEvent event) {
            staticLogs.add("MediumPriority");
            return false; // Stop propagation
        }

        @MangoBotEventListener
        @PluginPriority(10) // Lowest
        public boolean onLowPriority(GroupMessageEvent event) {
            staticLogs.add("LowPriority");
            return true;
        }
    }
}
