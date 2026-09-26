package io.github.mangomaner.mangobot.manager.model.onebot.event;

import org.junit.jupiter.api.Test;
import io.github.mangomaner.mangobot.adapter.onebot.model.event.OneBotEvent;
import io.github.mangomaner.mangobot.adapter.onebot.handler.inbound.json_to_event.OneBotEventParser;
import io.github.mangomaner.mangobot.events.onebot.message.OneBotGroupMessageEvent;
import io.github.mangomaner.mangobot.events.onebot.meta.OneBotHeartbeatEvent;
import io.github.mangomaner.mangobot.adapter.onebot.model.segment.ImageSegment;
import io.github.mangomaner.mangobot.adapter.onebot.model.segment.LocationSegment;
import io.github.mangomaner.mangobot.adapter.onebot.model.segment.TextSegment;
import io.github.mangomaner.mangobot.adapter.onebot.model.segment.OneBotMessageSegment;
import io.github.mangomaner.mangobot.events.onebot.notice.OneBotGroupBanEvent;
import io.github.mangomaner.mangobot.events.onebot.notice.OneBotGroupDecreaseEvent;
import io.github.mangomaner.mangobot.events.onebot.notice.PokeEvent;

import io.github.mangomaner.mangobot.events.onebot.notice.OneBotGroupRecallEvent;

import io.github.mangomaner.mangobot.adapter.onebot.model.segment.KeyboardSegment;
import io.github.mangomaner.mangobot.adapter.onebot.model.segment.MarkdownSegment;

import static org.junit.jupiter.api.Assertions.*;

public class EventParserTest {

    @Test
    public void testParseKeyboardAndMarkdown() throws Exception {
        String json = "{\"self_id\":1461626638,\"user_id\":3889000871,\"time\":1768201628,\"message_id\":-628804360,\"message_seq\":151052,\"message_type\":\"group\",\"sender\":{\"user_id\":3889000871,\"nickname\":\"战地1小电视\",\"card\":\"\",\"role\":\"member\",\"title\":\"\"},\"raw_message\":\"...\",\"font\":14,\"sub_type\":\"normal\",\"message\":[{\"type\":\"keyboard\",\"data\":{\"rows\":[{\"buttons\":[{\"id\":\"\",\"render_data\":{\"label\":\"武器\",\"visited_label\":\"武器\",\"style\":0},\"action\":{\"type\":2,\"permission\":{\"type\":2,\"specify_role_ids\":[],\"specify_user_ids\":[]},\"unsupport_tips\":\"\",\"data\":\"/weapon 0ushj\",\"reply\":false,\"enter\":true}}]}]}},{\"type\":\"markdown\",\"data\":{\"content\":\"test\"}}],\"message_format\":\"array\",\"post_type\":\"message\",\"raw_pb\":\"\",\"group_id\":958499874,\"group_name\":\"伟大至福的梁鑫【唐人街限定版】\"}";
        
        OneBotEvent event = OneBotEventParser.parse(json);
        assertTrue(event instanceof OneBotGroupMessageEvent);
        OneBotGroupMessageEvent groupEvent = (OneBotGroupMessageEvent) event;
        
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
    public void testParseGroupRecall() throws Exception {
        String json = "{\"time\":1768231994,\"self_id\":1461626638,\"post_type\":\"notice\",\"notice_type\":\"group_recall\",\"operator_id\":2756477287,\"message_id\":-1790873196,\"group_id\":220264051,\"user_id\":2756477287}";
        
        OneBotEvent event = OneBotEventParser.parse(json);
        assertTrue(event instanceof OneBotGroupRecallEvent);
        OneBotGroupRecallEvent recall = (OneBotGroupRecallEvent) event;
        
        assertEquals(220264051L, recall.getGroupId());
        assertEquals(2756477287L, recall.getOperatorId());
        assertEquals(-1790873196, recall.getMessageId());
    }

    @Test
    public void testParseTextGroupMessage() throws Exception {
        String json = "{\"self_id\":1461626638,\"user_id\":2756477287,\"time\":1768132933,\"message_id\":-2064532954,\"message_seq\":11925,\"message_type\":\"group\",\"sender\":{\"user_id\":2756477287,\"nickname\":\"盲果人\",\"card\":\"盲 果 人\",\"role\":\"owner\",\"title\":\"\"},\"raw_message\":\"纯文本测试\",\"font\":14,\"sub_type\":\"normal\",\"message\":[{\"type\":\"text\",\"data\":{\"text\":\"纯文本测试\"}}],\"message_format\":\"array\",\"post_type\":\"message\",\"raw_pb\":\"\",\"group_id\":220264051,\"group_name\":\"6\"}";
        
        OneBotEvent event = OneBotEventParser.parse(json);
        
        assertTrue(event instanceof OneBotGroupMessageEvent);
        OneBotGroupMessageEvent groupEvent = (OneBotGroupMessageEvent) event;
        
        assertEquals(2756477287L, groupEvent.getUserId());
        assertEquals(220264051L, groupEvent.getGroupId());
        assertEquals("纯文本测试", groupEvent.getRawMessage());
        assertEquals(1, groupEvent.getMessage().size());
        
        OneBotMessageSegment segment = groupEvent.getMessage().get(0);
        assertTrue(segment instanceof TextSegment);
        assertEquals("纯文本测试", ((TextSegment) segment).getText());
    }

    @Test
    public void testParsePokeNotice() throws Exception {
        String json = "{\"time\":1768148179,\"self_id\":1461626638,\"post_type\":\"notice\",\"notice_type\":\"notify\",\"sub_type\":\"poke\",\"target_id\":2756477287,\"user_id\":2756477287,\"group_id\":220264051,\"raw_info\":[]}";
        
        OneBotEvent event = OneBotEventParser.parse(json);
        
        assertTrue(event instanceof PokeEvent);
        PokeEvent poke = (PokeEvent) event;
        assertEquals("poke", poke.getSubType());
        assertEquals(2756477287L, poke.getTargetId());
        assertEquals(220264051L, poke.getGroupId());
    }

    @Test
    public void testParseGroupDecrease() throws Exception {
        String json = "{\"time\":1768185826,\"self_id\":1461626638,\"post_type\":\"notice\",\"notice_type\":\"group_decrease\",\"sub_type\":\"kick\",\"operator_id\":2756477287,\"group_id\":220264051,\"user_id\":2854213448}";
        
        OneBotEvent event = OneBotEventParser.parse(json);
        
        assertTrue(event instanceof OneBotGroupDecreaseEvent);
        OneBotGroupDecreaseEvent decrease = (OneBotGroupDecreaseEvent) event;
        assertEquals("kick", decrease.getSubType());
        assertEquals(2854213448L, decrease.getUserId());
    }

    @Test
    public void testParseGroupBan() throws Exception {
        String json = "{\"time\":1768186239,\"self_id\":1461626638,\"post_type\":\"notice\",\"notice_type\":\"group_ban\",\"operator_id\":2756477287,\"duration\":600,\"sub_type\":\"ban\",\"group_id\":220264051,\"user_id\":3970521445}";
        
        OneBotEvent event = OneBotEventParser.parse(json);
        
        assertTrue(event instanceof OneBotGroupBanEvent);
        OneBotGroupBanEvent ban = (OneBotGroupBanEvent) event;
        assertEquals(600, ban.getDuration());
        assertEquals(3970521445L, ban.getUserId());
    }

    @Test
    public void testParseImageMessage() throws Exception {
        String json = "{\"self_id\":1461626638,\"user_id\":2756477287,\"time\":1768132937,\"message_id\":-1514782682,\"message_seq\":11926,\"message_type\":\"group\",\"sender\":{\"user_id\":2756477287,\"nickname\":\"盲果人\",\"card\":\"盲 果 人\",\"role\":\"owner\",\"title\":\"\"},\"raw_message\":\"...\",\"font\":14,\"sub_type\":\"normal\",\"message\":[{\"type\":\"image\",\"data\":{\"file\":\"EE47B5F4C62F0B6D91B0EF6968816493.jpg\",\"subType\":1,\"url\":\"http://url\",\"file_size\":\"95799\"}}],\"message_format\":\"array\",\"post_type\":\"message\",\"raw_pb\":\"\",\"group_id\":220264051,\"group_name\":\"6\"}";
        
        OneBotEvent event = OneBotEventParser.parse(json);
        
        assertTrue(event instanceof OneBotGroupMessageEvent);
        OneBotGroupMessageEvent groupEvent = (OneBotGroupMessageEvent) event;
        OneBotMessageSegment segment = groupEvent.getMessage().get(0);
        assertTrue(segment instanceof ImageSegment);
        assertEquals("EE47B5F4C62F0B6D91B0EF6968816493.jpg", ((ImageSegment) segment).getData().getFile());
    }

    @Test
    public void testParseLocationMessageWithStringCoords() throws Exception {
        String json = "{\"self_id\":1461626638,\"user_id\":2756477287,\"time\":1768132937,\"message_id\":-1514782682,\"message_seq\":11926,\"message_type\":\"group\",\"sender\":{\"user_id\":2756477287,\"nickname\":\"盲果人\",\"card\":\"盲 果 人\",\"role\":\"owner\",\"title\":\"\"},\"raw_message\":\"[CQ:location]\",\"font\":14,\"sub_type\":\"normal\",\"message\":[{\"type\":\"location\",\"data\":{\"lat\":\"39.8969426\",\"lon\":\"116.3109099\",\"title\":\"北京市海淀区\",\"content\":\"中关村大街\"}}],\"message_format\":\"array\",\"post_type\":\"message\",\"raw_pb\":\"\",\"group_id\":220264051,\"group_name\":\"6\"}";

        OneBotEvent event = OneBotEventParser.parse(json);

        assertTrue(event instanceof OneBotGroupMessageEvent);
        OneBotGroupMessageEvent groupEvent = (OneBotGroupMessageEvent) event;
        OneBotMessageSegment segment = groupEvent.getMessage().get(0);
        assertTrue(segment instanceof LocationSegment);
        LocationSegment.LocationData data = ((LocationSegment) segment).getData();
        assertEquals("39.8969426", data.getLat());
        assertEquals("116.3109099", data.getLon());
        assertEquals("北京市海淀区", data.getTitle());
        assertEquals("中关村大街", data.getContent());
        assertEquals("location", segment.getType());
    }

    /**
     * NapCat 的 schema 里 lat/lon 是 {@code string | number} 二选一，数字形态必须也接得住：
     * 字段类型是 String，靠 Jackson 把数字读成文本。这条就是钉住这个取舍的。
     */
    @Test
    public void testParseLocationMessageWithNumericCoords() throws Exception {
        String json = "{\"self_id\":1461626638,\"user_id\":2756477287,\"time\":1768132937,\"message_id\":-1514782682,\"message_seq\":11926,\"message_type\":\"group\",\"sender\":{\"user_id\":2756477287,\"nickname\":\"盲果人\",\"card\":\"\",\"role\":\"owner\",\"title\":\"\"},\"raw_message\":\"[CQ:location]\",\"font\":14,\"sub_type\":\"normal\",\"message\":[{\"type\":\"location\",\"data\":{\"lat\":39.8969426,\"lon\":116.3109099}}],\"message_format\":\"array\",\"post_type\":\"message\",\"raw_pb\":\"\",\"group_id\":220264051,\"group_name\":\"6\"}";

        OneBotEvent event = OneBotEventParser.parse(json);

        OneBotGroupMessageEvent groupEvent = (OneBotGroupMessageEvent) event;
        LocationSegment.LocationData data = ((LocationSegment) groupEvent.getMessage().get(0)).getData();
        assertEquals("39.8969426", data.getLat());
        assertEquals("116.3109099", data.getLon());
    }

    @Test
    public void testParseHeartbeat() throws Exception {
        String json = "{\"time\":1768132988,\"self_id\":1461626638,\"post_type\":\"meta_event\",\"meta_event_type\":\"heartbeat\",\"status\":{\"online\":true,\"good\":true},\"interval\":60000}";
        
        OneBotEvent event = OneBotEventParser.parse(json);
        
        assertTrue(event instanceof OneBotHeartbeatEvent);
        OneBotHeartbeatEvent heartbeat = (OneBotHeartbeatEvent) event;
        assertTrue(heartbeat.getStatus().isOnline());
        assertEquals(60000, heartbeat.getInterval());
    }
}
