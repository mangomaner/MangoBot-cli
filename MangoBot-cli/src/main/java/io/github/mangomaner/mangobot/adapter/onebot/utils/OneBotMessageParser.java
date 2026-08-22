package io.github.mangomaner.mangobot.adapter.onebot.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.mangomaner.mangobot.adapter.onebot.model.segment.*;
import io.github.mangomaner.mangobot.adapter.onebot.model.vo.MessageInfo;
import io.github.mangomaner.mangobot.module.file.model.domain.BotFiles;
import io.github.mangomaner.mangobot.events.onebot.message.OneBotGroupMessageEvent;
import io.github.mangomaner.mangobot.module.file.service.BotFilesService;
import io.github.mangomaner.mangobot.adapter.onebot.handler.outbound.send.OneBotApiService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class OneBotMessageParser {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 常见 QQ 表情 id -> 名称（用于把 [表情123] 转成可读文本） */
    private static final Map<String, String> FACE_NAMES = Map.ofEntries(
            Map.entry("0", "惊讶"), Map.entry("1", "撇嘴"), Map.entry("4", "得意"),
            Map.entry("5", "流泪"), Map.entry("6", "害羞"), Map.entry("9", "大哭"),
            Map.entry("14", "微笑"), Map.entry("20", "偷笑"), Map.entry("21", "可爱"),
            Map.entry("57", "啤酒"), Map.entry("63", "玫瑰"), Map.entry("66", "爱心"),
            Map.entry("79", "强"), Map.entry("82", "胜利"), Map.entry("83", "抱拳"),
            Map.entry("171", "狗头"), Map.entry("174", "发怒"), Map.entry("175", "委屈"),
            Map.entry("178", "点赞"), Map.entry("179", "干杯"), Map.entry("319", "比心"),
            Map.entry("343", "害怕")
    );

    @Resource
    @Lazy
    private OneBotApiService oneBotApiService;

    @Resource
    private BotFilesService botFilesService;

    public String parseMessage(List<OneBotMessageSegment> segments, Long botId) {
        if (segments == null || segments.isEmpty()) {
            return "";
        }

        StringBuilder result = new StringBuilder();
        for (OneBotMessageSegment segment : segments) {
            String parsed = parseSegment(segment, botId);
            if (parsed != null && !parsed.isEmpty()) {
                if (result.length() > 0) {
                    result.append(' ');
                }
                result.append(parsed);
            }
        }
        return result.toString();
    }

    private String parseSegment(OneBotMessageSegment segment, Long botId) {
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
                case "reply":
                    return parseReplySegment((ReplySegment) segment, botId);
                default:
                    log.warn("Unknown message segment type: {}", type);
                    return "";
            }
        } catch (Exception e) {
            log.error("Failed to parse message segment: {}", segment, e);
            return "";
        }
    }

    private String parseReplySegment(ReplySegment segment, Long botId) {
        ReplySegment.ReplyData data = segment.getData();
        if (data == null) {
            return "[回复消息]";
        }
        try {
            MessageInfo info = oneBotApiService.getMsg(botId, Integer.parseInt(data.getId()));
            if (info == null) {
                return "[回复消息]";
            }
            String sender = extractSenderNickname(info.getSender());
            String text = extractReplyText(info.getMessage());
            StringBuilder result = new StringBuilder("回复");
            if (sender != null && !sender.isEmpty()) {
                result.append(sender);
            }
            if (text != null && !text.isEmpty()) {
                result.append("：").append(text);
            }
            return "[" + result + "]";
        } catch (Exception e) {
            log.warn("Failed to parse reply message: {}", data.getId(), e);
            return "[回复消息]";
        }
    }

    /**
     * 从 getMsg 返回的 sender（Object，实为 Map）中提取群名片/昵称/QQ。
     */
    private String extractSenderNickname(Object sender) {
        if (sender instanceof Map<?, ?> map) {
            Object card = map.get("card");
            if (card != null && !card.toString().isEmpty()) {
                return card.toString();
            }
            Object nickname = map.get("nickname");
            if (nickname != null && !nickname.toString().isEmpty()) {
                return nickname.toString();
            }
            Object userId = map.get("user_id");
            if (userId != null) {
                return userId.toString();
            }
        }
        return null;
    }

    /**
     * 从 getMsg 返回的 message（Object，可能为 String 或段数组）中提取纯文本。
     */
    private String extractReplyText(Object message) {
        if (message == null) {
            return null;
        }
        if (message instanceof String str) {
            return str;
        }
        if (message instanceof List<?> list) {
            StringBuilder sb = new StringBuilder();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Object text = map.get("text");
                    if (text != null) {
                        sb.append(text);
                    }
                }
            }
            return sb.toString();
        }
        return null;
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
        String id = data.getId();
        String name = FACE_NAMES.get(id);
        if (name != null) {
            return "[" + name + "表情]";
        }
        return "[表情" + id + "]";
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
        BotFiles fileByFileId = botFilesService.getFileByFileId(data.getFile());
        if (fileByFileId != null && fileByFileId.getDescription() != null) {
            return fileByFileId.getDescription();
        }

        Integer subTypeObj = data.getSubType();
        int subType = subTypeObj != null ? subTypeObj : 0;
        if (subType == 1 || subType == 11) {
            return "[表情]";
        }
        return "[图片]";
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
        if (segment.getData() == null) {
            return "";
        }
        return "[视频]";
    }

    private String parseRecordSegment(RecordSegment segment) {
        if (segment.getData() == null) {
            return "";
        }
        return "[语音]";
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

        List<OneBotGroupMessageEvent> event = null;
        event = oneBotApiService.getForwardMsg(botId, id);

        if (event == null || event.isEmpty()) {
            return "转发消息：[合并转发消息 ID=" + id + "]";
        }

        StringBuilder sb = new StringBuilder();
        for (OneBotGroupMessageEvent e : event) {
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
