package org.mango.mangobot.model.onebot;

import org.mango.mangobot.model.onebot.SendMessage;
import org.mango.mangobot.model.onebot.segment.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 消息构建器，用于构建消息链
 */
public class MessageBuilder {

    public static InitialState create() {
        return new Builder();
    }

    public static class Builder implements InitialState, TextState, AtState, AtAllState, ImageState, FaceState, ReplyState, RecordState, VideoState, ShakeState, NodeState, CustomNodeState{
        private final List<MessageSegment> segments = new ArrayList<>();
        /**
         * 添加纯文本
         * @param text 文本内容
         */
        @Override
        public TextState text(String text) {
            TextSegment segment = new TextSegment();
            TextSegment.TextData data = new TextSegment.TextData();
            data.setText(text);
            segment.setData(data);
            segment.setType("text");
            segments.add(segment);
            return this;
        }


        /**
         * 添加 @
         * @param qq QQ 号
         */
        @Override
        public AtState at(String qq) {
            AtSegment segment = new AtSegment();
            AtSegment.AtData data = new AtSegment.AtData();
            data.setQq(qq);
            segment.setData(data);
            segment.setType("at");
            segments.add(segment);
            return this;
        }

        /**
         * 添加 @全体成员
         */
        @Override
        public AtAllState atAll() {
            at("all");
            return this;
        }

        /**
         * 添加图片
         * @param file 图片文件名 (支持 file://, http://, base64://)
         * @param subType 图片子类型 (0: 默认, 1: 表情包)
         */
        @Override
        public ImageState image(String file, Integer subType) {
            ImageSegment segment = new ImageSegment();
            ImageSegment.ImageData data = new ImageSegment.ImageData();
            data.setFile(file);
            data.setSubType(subType);
            segment.setData(data);
            segment.setType("image");
            segments.add(segment);
            return this;
        }

        /**
         * 添加表情
         * @param id 表情 ID
         */
        @Override
        public FaceState face(String id) {
            FaceSegment segment = new FaceSegment();
            FaceSegment.FaceData data = new FaceSegment.FaceData();
            data.setId(id);
            segment.setData(data);
            segment.setType("face");
            segments.add(segment);
            return this;
        }

        /**
         * 回复
         * @param messageId 消息 ID
         */
        @Override
        public ReplyState reply(int messageId) {
            ReplySegment segment = new ReplySegment();
            ReplySegment.ReplyData data = new ReplySegment.ReplyData();
            data.setId(String.valueOf(messageId));
            segment.setData(data);
            segment.setType("reply");
            segments.add(segment);
            return this;
        }

        /**
         * 添加语音
         * @param file 语音文件名 (支持 file://, http://, base64://)
         */
        @Override
        public RecordState record(String file) {
            RecordSegment segment = new RecordSegment();
            RecordSegment.RecordData data = new RecordSegment.RecordData();
            data.setFile(file);
            segment.setData(data);
            segment.setType("record");
            segments.add(segment);
            return this;
        }

        /**
         * 添加视频
         * @param file 视频文件名 (支持 file://, http://, base64://)
         */
        @Override
        public VideoState video(String file) {
            VideoSegment segment = new VideoSegment();
            VideoSegment.VideoData data = new VideoSegment.VideoData();
            data.setFile(file);
            segment.setData(data);
            segment.setType("video");
            segments.add(segment);
            return this;
        }

        /**
         * 添加窗口抖动 (戳一戳)
         */
        @Override
        public ShakeState shake() {
            ShakeSegment segment = new ShakeSegment();
            segment.setData(new ShakeSegment.ShakeData());
            segment.setType("shake");
            segments.add(segment);
            return this;
        }

        /**
         * 合并转发节点 (引用已有消息)
         * @param id 消息 ID
         */
        @Override
        public NodeState node(String id) {
            NodeSegment segment = new NodeSegment();
            NodeSegment.NodeData data = new NodeSegment.NodeData();
            data.setId(id);
            segment.setData(data);
            segment.setType("node");
            segments.add(segment);
            return this;
        }

        /**
         * 合并转发节点 (自定义内容)
         * @param userId 发送者 QQ
         * @param nickname 发送者昵称
         * @param content 消息内容
         */
        @Override
        public CustomNodeState customNode(String userId, String nickname, Object content) {
            NodeSegment segment = new NodeSegment();
            NodeSegment.NodeData data = new NodeSegment.NodeData();
            data.setUserId(userId);
            data.setNickname(nickname);
            data.setContent(content);
            segment.setData(data);
            segment.setType("node");
            segments.add(segment);
            return this;
        }

        public void add(MessageSegment segment) {
            segments.add(segment);
        }

        public SendMessage build() {
            SendMessage message = new SendMessage();
            message.setMessage(segments);
            return message;
        }
    }


    // —————————— 下列是对api接口的限制 —————————— //

    public interface InitialState {
        TextState text(String text);
        AtState at(String qq);
        AtAllState atAll();
        ImageState image(String file, Integer subType);
        FaceState face(String id);
        ReplyState reply(int messageId);
        RecordState record(String file);
        VideoState video(String file);
        ShakeState shake();
        NodeState node(String id);
        CustomNodeState customNode(String userId, String nickname, Object content);
    }

    public interface TextState {
        AtState at(String qq);
        AtAllState atAll();
        ImageState image(String file, Integer subType);
        FaceState face(String id);
        SendMessage build();
    }

    public interface AtState {
        TextState text(String text);
        AtState at(String qq);
        AtAllState atAll();
        ImageState image(String file, Integer subType);
        FaceState face(String id);
        SendMessage build();
    }

    public interface AtAllState {
        TextState text(String text);
        AtState at(String qq);
        AtAllState atAll();
        ImageState image(String file, Integer subType);
        FaceState face(String id);
        SendMessage build();
    }

    public interface ImageState {
        TextState text(String text);
        AtState at(String qq);
        AtAllState atAll();
        ImageState image(String file, Integer subType);
        FaceState face(String id);
        SendMessage build();
    }

    public interface FaceState {
        TextState text(String text);
        AtState at(String qq);
        AtAllState atAll();
        ImageState image(String file, Integer subType);
        FaceState face(String id);
        SendMessage build();
    }

    public interface ReplyState {
        TextState text(String text);
        AtState at(String qq);
        AtAllState atAll();
        ImageState image(String file, Integer subType);
        FaceState face(String id);
        SendMessage build();
    }

    public interface RecordState {
        SendMessage build();
    }

    public interface VideoState {
        SendMessage build();
    }

    public interface ShakeState {
        SendMessage build();
    }

    public interface NodeState {
        NodeState node(String id);
        SendMessage build();
    }

    public interface CustomNodeState {
        CustomNodeState customNode(String userId, String nickname, Object content);
        SendMessage build();
    }

}


//
///**
// * JSON 消息
// */
//public MessageBuilder json(String jsonData) {
//    JsonSegment segment = new JsonSegment();
//    JsonSegment.JsonData data = new JsonSegment.JsonData();
//    data.setData(jsonData);
//    segment.setData(data);
//    segment.setType("json");
//    segments.add(segment);
//    return this;
//}
//
///**
// * 发送位置
// * @param lat 纬度
// * @param lon 经度
// * @param title 标题
// * @param content 内容描述
// */
//public MessageBuilder location(String lat, String lon, String title, String content) {
//    LocationSegment segment = new LocationSegment();
//    LocationSegment.LocationData data = new LocationSegment.LocationData();
//    data.setLat(lat);
//    data.setLon(lon);
//    data.setTitle(title);
//    data.setContent(content);
//    segment.setData(data);
//    segment.setType("location");
//    segments.add(segment);
//    return this;
//}
//
///**
// * 添加戳一戳 (高级)
// * @param type 类型 (见 Mirai PokeMessage)
// * @param id ID (见 Mirai PokeMessage)
// */
//public MessageBuilder poke(String type, String id) {
//    PokeSegment segment = new PokeSegment();
//    PokeSegment.PokeData data = new PokeSegment.PokeData();
//    data.setType(type);
//    data.setId(id);
//    segment.setData(data);
//    segment.setType("poke");
//    segments.add(segment);
//    return this;
//}
///**
// * 链接分享
// * @param url URL
// * @param title 标题
// * @param content 内容描述
// * @param image 图片 URL
// */
//public MessageBuilder share(String url, String title, String content, String image) {
//    ShareSegment segment = new ShareSegment();
//    ShareSegment.ShareData data = new ShareSegment.ShareData();
//    data.setUrl(url);
//    data.setTitle(title);
//    data.setContent(content);
//    data.setImage(image);
//    segment.setData(data);
//    segment.setType("share");
//    segments.add(segment);
//    return this;
//}