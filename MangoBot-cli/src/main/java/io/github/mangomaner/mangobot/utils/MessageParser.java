package io.github.mangomaner.mangobot.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mangomaner.mangobot.model.onebot.segment.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class MessageParser {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public String parseMessage(List<MessageSegment> segments) {
        if (segments == null || segments.isEmpty()) {
            return "";
        }

        StringBuilder result = new StringBuilder();
        for (MessageSegment segment : segments) {
            String parsed = parseSegment(segment);
            if (parsed != null && !parsed.isEmpty()) {
                result.append(parsed);
            }
        }
        return result.toString();
    }

    private String parseSegment(MessageSegment segment) {
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
                    return parseForwardSegment((ForwardSegment) segment);
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
            return "发送害怕的表情";
        }
        if (subType == 3 && "319".equals(id)) {
            return "发送比心的表情";
        }
        return "发送表情[" + id + "]";
    }

    private String parseFileSegment(FileSegment segment) {
        FileSegment.FileData data = segment.getData();
        if (data == null) {
            return "";
        }
        String url = data.getUrl();
        if (url != null && !url.isEmpty()) {
            return "发送文件：" + url;
        }
        return "发送文件";
    }

    private String parseImageSegment(ImageSegment segment) {
        ImageSegment.ImageData data = segment.getData();
        if (data == null) {
            return "";
        }
        int subType = data.getSubType();
        String url = data.getUrl();
        
        if (subType == 1) {
            if (url != null && !url.isEmpty()) {
                return "发送表情：" + url;
            }
            return "发送表情";
        } else {
            if (url != null && !url.isEmpty()) {
                return "发送图片：" + url;
            }
            return "发送图片";
        }
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
            jsonNode.fields().forEachRemaining(entry -> {
                result.append(entry.getKey()).append(":").append(entry.getValue().asText()).append(" ");
            });
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
            return "发送视频：" + url;
        }
        return "发送视频";
    }

    private String parseRecordSegment(RecordSegment segment) {
        RecordSegment.RecordData data = segment.getData();
        if (data == null) {
            return "";
        }
        String url = data.getUrl();
        if (url != null && !url.isEmpty()) {
            return "发送语音：" + url;
        }
        return "发送语音";
    }

    private String parseForwardSegment(ForwardSegment segment) {
        ForwardSegment.ForwardData data = segment.getData();
        if (data == null) {
            return "";
        }
        String id = data.getId();
        if (id == null || id.isEmpty()) {
            return "转发消息";
        }
        return "转发消息：[合并转发消息 ID=" + id + "]";
    }
}
