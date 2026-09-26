package io.github.mangomaner.mangobot.adapter.onebot.handler.outbound.build_sending_message;

import io.github.mangomaner.mangobot.adapter.onebot.model.segment.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 消息构建器，用于构建消息链
 * 1. 添加视频、添加语音、添加窗口抖动只能单独使用，不可与其他任何元素一起添加，且只能添加一次
 * 2. 合并转发节点（引用已有信息）和合并转发节点（自定义内容）只能单独使用，但可以添加多次
 * 3. 回复只能添加一次，且在最开头添加
 * 4. 添加文本不能连续添加，中间必须间隔东西
 *
 * <p><b>发位置不在这里。</b>OneBot 的 {@code location} 段实测在当前 NapCat/QQ 上发出去客户端不认
 * （见同包的 {@link LocationCard}），位置只能发成腾讯地图 Ark 卡片；那个形状要带 bot 号与自己所在的
 * 群/好友号，而本 Builder 是无上下文的，所以挪到了
 * {@code MangoOneBotApi.sendGroupLocation} / {@code sendPrivateLocation}。
 */
public class OneBotMessageBuilder {

    public static InitialState create() {
        return new Builder();
    }

    public static class Builder implements InitialState, TextState, AtState, AtAllState, ImageState, FaceState, ReplyState, RecordState, VideoState, ShakeState, NodeState, CustomNodeState{
        private final List<OneBotMessageSegment> segments = new ArrayList<>();
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
         * @param isMeme 是否为表情包 (0: 否, 1: 表情包)
         */
        @Override
        public ImageState image(String file, Boolean isMeme) {
            ImageSegment segment = new ImageSegment();
            ImageSegment.ImageData data = new ImageSegment.ImageData();
            data.setFile(file);
            data.setSubType(Boolean.TRUE.equals(isMeme) ? 1 : null);
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

        public void add(OneBotMessageSegment segment) {
            segments.add(segment);
        }

        public OneBotSendingMessage build() {
            OneBotSendingMessage message = new OneBotSendingMessage();
            message.setMessage(segments);
            return message;
        }
    }


    // —————————— 下列是对api接口的限制 —————————— //

    public interface InitialState {
        TextState text(String text);
        AtState at(String qq);
        AtAllState atAll();
        ImageState image(String file, Boolean isMeme);
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
        ImageState image(String file, Boolean isMeme);
        FaceState face(String id);
        OneBotSendingMessage build();
    }

    public interface AtState {
        TextState text(String text);
        AtState at(String qq);
        AtAllState atAll();
        ImageState image(String file, Boolean isMeme);
        FaceState face(String id);
        OneBotSendingMessage build();
    }

    public interface AtAllState {
        TextState text(String text);
        AtState at(String qq);
        AtAllState atAll();
        ImageState image(String file, Boolean isMeme);
        FaceState face(String id);
        OneBotSendingMessage build();
    }

    public interface ImageState {
        TextState text(String text);
        AtState at(String qq);
        AtAllState atAll();
        ImageState image(String file, Boolean isMeme);
        FaceState face(String id);
        OneBotSendingMessage build();
    }

    public interface FaceState {
        TextState text(String text);
        AtState at(String qq);
        AtAllState atAll();
        ImageState image(String file, Boolean isMeme);
        FaceState face(String id);
        OneBotSendingMessage build();
    }

    public interface ReplyState {
        TextState text(String text);
        AtState at(String qq);
        AtAllState atAll();
        ImageState image(String file, Boolean isMeme);
        FaceState face(String id);
        OneBotSendingMessage build();
    }

    public interface RecordState {
        OneBotSendingMessage build();
    }

    public interface VideoState {
        OneBotSendingMessage build();
    }

    public interface ShakeState {
        OneBotSendingMessage build();
    }

    public interface NodeState {
        NodeState node(String id);
        OneBotSendingMessage build();
    }

    public interface CustomNodeState {
        CustomNodeState customNode(String userId, String nickname, Object content);
        OneBotSendingMessage build();
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