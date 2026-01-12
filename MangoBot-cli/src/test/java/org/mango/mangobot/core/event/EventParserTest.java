package org.mango.mangobot.core.event;

import org.junit.jupiter.api.Test;
import org.mango.mangobot.model.onebot.event.Event;
import org.mango.mangobot.model.onebot.event.EventParser;
import org.mango.mangobot.model.onebot.event.message.GroupMessageEvent;
import org.mango.mangobot.model.onebot.event.meta.HeartbeatEvent;
import org.mango.mangobot.model.onebot.segment.ImageSegment;
import org.mango.mangobot.model.onebot.segment.TextSegment;
import org.mango.mangobot.model.onebot.segment.MessageSegment;
import org.mango.mangobot.model.onebot.event.notice.GroupBanEvent;
import org.mango.mangobot.model.onebot.event.notice.GroupDecreaseEvent;
import org.mango.mangobot.model.onebot.event.notice.PokeEvent;

import org.mango.mangobot.model.onebot.segment.KeyboardSegment;
import org.mango.mangobot.model.onebot.segment.MarkdownSegment;

import static org.junit.jupiter.api.Assertions.*;

public class EventParserTest {

    @Test
    public void testParseKeyboardAndMarkdown() throws Exception {
        String json = "{\"self_id\":1461626638,\"user_id\":3889000871,\"time\":1768201628,\"message_id\":-628804360,\"message_seq\":151052,\"message_type\":\"group\",\"sender\":{\"user_id\":3889000871,\"nickname\":\"战地1小电视\",\"card\":\"\",\"role\":\"member\",\"title\":\"\"},\"raw_message\":\"...\",\"font\":14,\"sub_type\":\"normal\",\"message\":[{\"type\":\"keyboard\",\"data\":{\"rows\":[{\"buttons\":[{\"id\":\"\",\"render_data\":{\"label\":\"武器\",\"visited_label\":\"武器\",\"style\":0},\"action\":{\"type\":2,\"permission\":{\"type\":2,\"specify_role_ids\":[],\"specify_user_ids\":[]},\"unsupport_tips\":\"\",\"data\":\"/weapon 0ushj\",\"reply\":false,\"enter\":true}}]}]}},{\"type\":\"markdown\",\"data\":{\"content\":\"test\"}}],\"message_format\":\"array\",\"post_type\":\"message\",\"raw_pb\":\"\",\"group_id\":958499874,\"group_name\":\"伟大至福的梁鑫【唐人街限定版】\"}";
        
        Event event = EventParser.parse(json);
        assertTrue(event instanceof GroupMessageEvent);
        GroupMessageEvent groupEvent = (GroupMessageEvent) event;
        
        assertEquals(2, groupEvent.getMessage().size());
        
        assertTrue(groupEvent.getMessage().get(0) instanceof KeyboardSegment);
        KeyboardSegment keyboard = (KeyboardSegment) groupEvent.getMessage().get(0);
        assertNotNull(keyboard.getData().getRows());
        assertEquals("武器", keyboard.getData().getRows().get(0).getButtons().get(0).getRenderData().getLabel());
        
        assertTrue(groupEvent.getMessage().get(1) instanceof MarkdownSegment);
        MarkdownSegment markdown = (MarkdownSegment) groupEvent.getMessage().get(1);
        assertEquals("test", markdown.getData().getContent());
    }

    @Test
    public void testParseTextGroupMessage() throws Exception {
        String json = "{\"self_id\":1461626638,\"user_id\":2756477287,\"time\":1768132933,\"message_id\":-2064532954,\"message_seq\":11925,\"message_type\":\"group\",\"sender\":{\"user_id\":2756477287,\"nickname\":\"盲果人\",\"card\":\"盲 果 人\",\"role\":\"owner\",\"title\":\"\"},\"raw_message\":\"纯文本测试\",\"font\":14,\"sub_type\":\"normal\",\"message\":[{\"type\":\"text\",\"data\":{\"text\":\"纯文本测试\"}}],\"message_format\":\"array\",\"post_type\":\"message\",\"raw_pb\":\"\",\"group_id\":220264051,\"group_name\":\"6\"}";
        
        Event event = EventParser.parse(json);
        
        assertTrue(event instanceof GroupMessageEvent);
        GroupMessageEvent groupEvent = (GroupMessageEvent) event;
        
        assertEquals(2756477287L, groupEvent.getUserId());
        assertEquals(220264051L, groupEvent.getGroupId());
        assertEquals("纯文本测试", groupEvent.getRawMessage());
        assertEquals(1, groupEvent.getMessage().size());
        
        MessageSegment segment = groupEvent.getMessage().get(0);
        assertTrue(segment instanceof TextSegment);
        assertEquals("纯文本测试", ((TextSegment) segment).getText());
    }

    @Test
    public void testParsePokeNotice() throws Exception {
        String json = "{\"time\":1768148179,\"self_id\":1461626638,\"post_type\":\"notice\",\"notice_type\":\"notify\",\"sub_type\":\"poke\",\"target_id\":2756477287,\"user_id\":2756477287,\"group_id\":220264051,\"raw_info\":[]}";
        
        Event event = EventParser.parse(json);
        
        assertTrue(event instanceof PokeEvent);
        PokeEvent poke = (PokeEvent) event;
        assertEquals("poke", poke.getSubType());
        assertEquals(2756477287L, poke.getTargetId());
        assertEquals(220264051L, poke.getGroupId());
    }

    @Test
    public void testParseGroupDecrease() throws Exception {
        String json = "{\"time\":1768185826,\"self_id\":1461626638,\"post_type\":\"notice\",\"notice_type\":\"group_decrease\",\"sub_type\":\"kick\",\"operator_id\":2756477287,\"group_id\":220264051,\"user_id\":2854213448}";
        
        Event event = EventParser.parse(json);
        
        assertTrue(event instanceof GroupDecreaseEvent);
        GroupDecreaseEvent decrease = (GroupDecreaseEvent) event;
        assertEquals("kick", decrease.getSubType());
        assertEquals(2854213448L, decrease.getUserId());
    }

    @Test
    public void testParseGroupBan() throws Exception {
        String json = "{\"time\":1768186239,\"self_id\":1461626638,\"post_type\":\"notice\",\"notice_type\":\"group_ban\",\"operator_id\":2756477287,\"duration\":600,\"sub_type\":\"ban\",\"group_id\":220264051,\"user_id\":3970521445}";
        
        Event event = EventParser.parse(json);
        
        assertTrue(event instanceof GroupBanEvent);
        GroupBanEvent ban = (GroupBanEvent) event;
        assertEquals(600, ban.getDuration());
        assertEquals(3970521445L, ban.getUserId());
    }

    @Test
    public void testParseImageMessage() throws Exception {
        String json = "{\"self_id\":1461626638,\"user_id\":2756477287,\"time\":1768132937,\"message_id\":-1514782682,\"message_seq\":11926,\"message_type\":\"group\",\"sender\":{\"user_id\":2756477287,\"nickname\":\"盲果人\",\"card\":\"盲 果 人\",\"role\":\"owner\",\"title\":\"\"},\"raw_message\":\"...\",\"font\":14,\"sub_type\":\"normal\",\"message\":[{\"type\":\"image\",\"data\":{\"file\":\"EE47B5F4C62F0B6D91B0EF6968816493.jpg\",\"subType\":1,\"url\":\"http://url\",\"file_size\":\"95799\"}}],\"message_format\":\"array\",\"post_type\":\"message\",\"raw_pb\":\"\",\"group_id\":220264051,\"group_name\":\"6\"}";
        
        Event event = EventParser.parse(json);
        
        assertTrue(event instanceof GroupMessageEvent);
        GroupMessageEvent groupEvent = (GroupMessageEvent) event;
        MessageSegment segment = groupEvent.getMessage().get(0);
        assertTrue(segment instanceof ImageSegment);
        assertEquals("EE47B5F4C62F0B6D91B0EF6968816493.jpg", ((ImageSegment) segment).getData().getFile());
    }

    @Test
    public void testParseHeartbeat() throws Exception {
        String json = "{\"time\":1768132988,\"self_id\":1461626638,\"post_type\":\"meta_event\",\"meta_event_type\":\"heartbeat\",\"status\":{\"online\":true,\"good\":true},\"interval\":60000}";
        
        Event event = EventParser.parse(json);
        
        assertTrue(event instanceof HeartbeatEvent);
        HeartbeatEvent heartbeat = (HeartbeatEvent) event;
        assertTrue(heartbeat.getStatus().isOnline());
        assertEquals(60000, heartbeat.getInterval());
    }
}
