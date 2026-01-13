package org.mango.mangobot.model.onebot.api;

import org.mango.mangobot.model.onebot.SendMessage;
import org.mango.mangobot.model.onebot.segment.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 消息构建器，用于构建消息链
 * 实现了严格的构建逻辑约束：
 * 1. 独占元素（视频、语音、窗口抖动）只能单独添加且只能添加一次
 * 2. 节点元素（合并转发）只能与其他节点元素混合，不可混用其他类型
 * 3. 回复只能添加一次，且必须在开头
 * 4. 文本不能连续添加
 */
public class MessageBuilder {

    public static InitialBuilder create() {
        return new BuilderImpl();
    }

    public interface ContentBuilder {
        SendMessage build();
    }

    public interface InitialBuilder extends ContentBuilder {
        ContentBuilder video(String file);
        ContentBuilder record(String file);
        ContentBuilder shake();
        NodeBuilder node(String id);
        NodeBuilder customNode(String userId, String nickname, Object content);
        TextAllowedBuilder reply(int messageId);
        TextForbiddenBuilder text(String text);
        
        TextAllowedBuilder at(String qq);
        TextAllowedBuilder atAll();
        TextAllowedBuilder image(String file, Integer subType);
        TextAllowedBuilder face(String id);
    }

    public interface NodeBuilder extends ContentBuilder {
        NodeBuilder node(String id);
        NodeBuilder customNode(String userId, String nickname, Object content);
    }

    public interface TextAllowedBuilder extends ContentBuilder {
        TextForbiddenBuilder text(String text);
        
        TextAllowedBuilder at(String qq);
        TextAllowedBuilder atAll();
        TextAllowedBuilder image(String file, Integer subType);
        TextAllowedBuilder face(String id);
    }

    public interface TextForbiddenBuilder extends ContentBuilder {
        // No text() method
        
        TextAllowedBuilder at(String qq);
        TextAllowedBuilder atAll();
        TextAllowedBuilder image(String file, Integer subType);
        TextAllowedBuilder face(String id);
        // ... add other normal segments here
    }

    // --- Implementation ---

    private static class BuilderImpl implements InitialBuilder, NodeBuilder, TextAllowedBuilder, TextForbiddenBuilder {
        private final List<MessageSegment> segments = new ArrayList<>();

        @Override
        public SendMessage build() {
            SendMessage message = new SendMessage();
            message.setMessage(segments);
            return message;
        }

        private void addSegment(MessageSegment segment) {
            segments.add(segment);
        }


        @Override
        public ContentBuilder video(String file) {
            VideoSegment segment = new VideoSegment();
            VideoSegment.VideoData data = new VideoSegment.VideoData();
            data.setFile(file);
            segment.setData(data);
            segment.setType("video");
            addSegment(segment);
            return this;
        }

        @Override
        public ContentBuilder record(String file) {
            RecordSegment segment = new RecordSegment();
            RecordSegment.RecordData data = new RecordSegment.RecordData();
            data.setFile(file);
            segment.setData(data);
            segment.setType("record");
            addSegment(segment);
            return this;
        }

        @Override
        public ContentBuilder shake() {
            ShakeSegment segment = new ShakeSegment();
            segment.setData(new ShakeSegment.ShakeData());
            segment.setType("shake");
            addSegment(segment);
            return this;
        }

        // --- Nodes ---

        @Override
        public NodeBuilder node(String id) {
            NodeSegment segment = new NodeSegment();
            NodeSegment.NodeData data = new NodeSegment.NodeData();
            data.setId(id);
            segment.setData(data);
            segment.setType("node");
            addSegment(segment);
            return this;
        }

        @Override
        public NodeBuilder customNode(String userId, String nickname, Object content) {
            NodeSegment segment = new NodeSegment();
            NodeSegment.NodeData data = new NodeSegment.NodeData();
            data.setUserId(userId);
            data.setNickname(nickname);
            data.setContent(content);
            segment.setData(data);
            segment.setType("node");
            addSegment(segment);
            return this;
        }

        // --- Reply ---

        @Override
        public TextAllowedBuilder reply(int messageId) {
            ReplySegment segment = new ReplySegment();
            ReplySegment.ReplyData data = new ReplySegment.ReplyData();
            data.setId(String.valueOf(messageId));
            segment.setData(data);
            segment.setType("reply");
            addSegment(segment);
            return this;
        }

        // --- Text ---

        @Override
        public TextForbiddenBuilder text(String text) {
            TextSegment segment = new TextSegment();
            TextSegment.TextData data = new TextSegment.TextData();
            data.setText(text);
            segment.setData(data);
            segment.setType("text");
            addSegment(segment);
            return this;
        }

        // --- Normal Segments (At, Image, Face, etc.) ---

        private TextAllowedBuilder addNormal(MessageSegment segment) {
            addSegment(segment);
            return this;
        }

        @Override
        public TextAllowedBuilder at(String qq) {
            AtSegment segment = new AtSegment();
            AtSegment.AtData data = new AtSegment.AtData();
            data.setQq(qq);
            segment.setData(data);
            segment.setType("at");
            return addNormal(segment);
        }

        @Override
        public TextAllowedBuilder atAll() {
            return at("all");
        }

        @Override
        public TextAllowedBuilder image(String file, Integer subType) {
            ImageSegment segment = new ImageSegment();
            ImageSegment.ImageData data = new ImageSegment.ImageData();
            data.setFile(file);
            data.setSubType(subType);
            segment.setData(data);
            segment.setType("image");
            return addNormal(segment);
        }

        @Override
        public TextAllowedBuilder face(String id) {
            FaceSegment segment = new FaceSegment();
            FaceSegment.FaceData data = new FaceSegment.FaceData();
            data.setId(id);
            segment.setData(data);
            segment.setType("face");
            return addNormal(segment);
        }
    }
}
