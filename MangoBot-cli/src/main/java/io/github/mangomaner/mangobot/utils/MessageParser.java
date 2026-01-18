package io.github.mangomaner.mangobot.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mangomaner.mangobot.model.dto.AddFileRequest;
import io.github.mangomaner.mangobot.model.onebot.event.Event;
import io.github.mangomaner.mangobot.model.onebot.event.EventParser;
import io.github.mangomaner.mangobot.model.onebot.event.message.GroupMessageEvent;
import io.github.mangomaner.mangobot.model.onebot.segment.*;
import io.github.mangomaner.mangobot.service.FilesService;
import io.github.mangomaner.mangobot.service.OneBotApiService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class MessageParser {

    private final ObjectMapper objectMapper = new ObjectMapper();
    @Resource
    private OneBotApiService oneBotApiService;

    public String parseMessage(List<MessageSegment> segments, Long botId) {
        if (segments == null || segments.isEmpty()) {
            return "";
        }

        StringBuilder result = new StringBuilder();
        for (MessageSegment segment : segments) {
            String parsed = parseSegment(segment, botId);
            if (parsed != null && !parsed.isEmpty()) {
                result.append(parsed);
            }
        }
        return result.toString();
    }

    private String parseSegment(MessageSegment segment, Long botId) {
        try {
            String type = segment.getType();
            switch (type) {
                case "text":
                    return parseTextSegment((TextSegment) segment);
                case "at":
                    return parseAtSegment((AtSegment) segment);
                case "face":
                    return parseFaceSegment((FaceSegment) segment);
                case "file":
                    return parseFileSegment((FileSegment) segment);
                case "image":
                    return parseImageSegment((ImageSegment) segment);
                case "json":
                    return parseJsonSegment((JsonSegment) segment);
                case "video":
                    return parseVideoSegment((VideoSegment) segment);
                case "record":
                    return parseRecordSegment((RecordSegment) segment);
                case "forward":
                    return parseForwardSegment((ForwardSegment) segment, botId);
                default:
                    log.warn("Unknown message segment type: {}", type);
                    return "";
            }
        } catch (Exception e) {
            log.error("Failed to parse message segment: {}", segment, e);
            return "";
        }
    }

    private String parseTextSegment(TextSegment segment) {
        return segment.getText();
    }

    private String parseAtSegment(AtSegment segment) {
        AtSegment.AtData data = segment.getData();
        if (data == null) {
            return "";
        }
        String name = data.getName();
        String qq = data.getQq();
        if (name != null && !name.isEmpty()) {
            return "@" + name + "(" + qq + ")";
        }
        return "@" + qq;
    }

    private String parseFaceSegment(FaceSegment segment) {
        FaceSegment.FaceData data = segment.getData();
        if (data == null) {
            return "";
        }
        int subType = data.getSubType();
        String id = data.getId();
        
        if (subType == 3 && "343".equals(id)) {
            return "害怕的表情";
        }
        if (subType == 3 && "319".equals(id)) {
            return "比心的表情";
        }
        return "表情[" + id + "]";
    }

    private String parseFileSegment(FileSegment segment) {
        FileSegment.FileData data = segment.getData();
        if (data == null) {
            return "";
        }
        String file = data.getFile();
        if (file != null && !file.isEmpty()) {
            return "文件：" + file;
        }
        return "文件";
    }

    private String parseImageSegment(ImageSegment segment) {
        ImageSegment.ImageData data = segment.getData();
        if (data == null) {
            return "";
        }
        int subType = data.getSubType();
        String url = data.getUrl();

        return switch (subType) {
            case 0 -> {         // 发送的手机图片，0为普通图片
                if (url != null && !url.isEmpty()) {
                    yield "图片：" + url;
                }
                yield "图片";
            }
            case 1, 11 -> {             // 1为QQ收藏的表情包，11为发送的gif图片
                if (url != null && !url.isEmpty()) {
                    yield "表情：" + url;
                }
                yield "表情";
            }
            default -> "图片";
        };
    }

    private String parseJsonSegment(JsonSegment segment) {
        JsonSegment.JsonData data = segment.getData();
        if (data == null) {
            return "";
        }
        String jsonStr = data.getData();
        if (jsonStr == null || jsonStr.isEmpty()) {
            return "";
        }
        
        try {
            JsonNode jsonNode = objectMapper.readTree(jsonStr);
            StringBuilder result = new StringBuilder();
            if(jsonNode.has("prompt")){
                result.append(jsonNode.get("prompt").asText()).append(" ");
            } else {
                jsonNode.fields().forEachRemaining(entry -> {
                    result.append(entry.getKey()).append(":").append(entry.getValue().asText()).append(" ");
                });
            }

            return result.toString();
        } catch (Exception e) {
            log.error("Failed to parse JSON segment: {}", jsonStr, e);
            return jsonStr;
        }
    }

    private String parseVideoSegment(VideoSegment segment) {
        VideoSegment.VideoData data = segment.getData();

        if (data == null) {
            return "";
        }
        String url = data.getUrl();

        if (url != null && !url.isEmpty()) {
            return "视频：" + url;
        }
        return "视频";
    }

    private String parseRecordSegment(RecordSegment segment) {
        RecordSegment.RecordData data = segment.getData();
        if (data == null) {
            return "";
        }
        String url = data.getUrl();

        if (url != null && !url.isEmpty()) {
            return "语音：" + url;
        }
        return "语音";
    }

    private String parseForwardSegment(ForwardSegment segment, Long botId) {
        ForwardSegment.ForwardData data = segment.getData();
        if (data == null) {
            return "";
        }
        String id = data.getId();

        if (id == null || id.isEmpty()) {
            return "转发消息";
        }

        List<GroupMessageEvent> event = null;
        event = oneBotApiService.getForwardMsg(botId, id);

        if (event == null || event.isEmpty()) {
            return "转发消息：[合并转发消息 ID=" + id + "]";
        }

        StringBuilder sb = new StringBuilder();
        for (GroupMessageEvent e : event) {
            String message = parseMessage(e.getMessage(), botId);
            sb.append(e.getSender().getNickname()).append("发送消息：").append(message).append("\n");
        }
        String result = sb.toString();

        if(!result.isEmpty()){
            return "转发消息：\n" + result;
        } else {
            return "转发消息：[合并转发消息 ID=" + id + "]";
        }
    }
}
