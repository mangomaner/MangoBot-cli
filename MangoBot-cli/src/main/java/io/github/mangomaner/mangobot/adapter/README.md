# Adapter 模块

适配器模块负责处理外部平台连接和消息协议转换，采用**适配器模式**实现多平台协议的统一接入。

## 模块结构

```
adapter/
├── WebSocketProtocolAdapter.java          # WebSocket 协议适配器接口
├── controller/                            # 通用连接控制器
│   └── ConnectionController.java          # 平台列表 API
├── model/                                 # 通用模型
│   ├── enums/
│   │   ├── PlatformType.java              # 平台类型枚举
│   │   └── ConnectionStatus.java          # 连接状态枚举
│   └── vo/
│       └── PlatformOptionVO.java          # 平台选项 VO
└── onebot/                                # OneBot 协议实现
    ├── controller/                        # 控制器层
    │   ├── OneBotConfigController.java    # 配置管理 API
    │   └── OneBotApiController.java       # OneBot API 控制器
    ├── handler/                           # 处理器
    │   ├── echo/                          # Echo 响应处理
    │   │   ├── OneBotEchoHandler.java     # 异步转同步处理器
    │   │   └── OneBotApiResponse.java     # API 响应模型
    │   ├── inbound/                       # 入站消息处理
    │   │   ├── json_to_event/             # JSON → 事件
    │   │   │   ├── OneBotEventParser.java
    │   │   │   └── OneBotEventDeserializer.java
    │   │   └── receive_websocket_message/ # WebSocket 消息处理
    │   │       └── OneBotMessageHandler.java
    │   └── outbound/                      # 出站消息处理
    │       ├── send/                      # 发送消息
    │       │   └── OneBotApiService.java
    │       └── build_sending_message/     # 构建消息（纯函数，不碰网络）
    │           ├── OneBotMessageBuilder.java
    │           ├── OneBotSendingMessage.java
    │           └── LocationCard.java      # 腾讯地图位置卡片段工厂（发位置只有这一条路）
    ├── model/                             # 数据模型
    │   ├── domain/
    │   │   └── OneBotConfig.java          # 配置实体
    │   ├── dto/
    │   │   ├── OneBotApiRequest.java      # API 请求
    │   │   ├── CreateOneBotConfigRequest.java
    │   │   └── UpdateOneBotConfigRequest.java
    │   ├── event/                         # 事件模型
    │   │   ├── OneBotEvent.java           # 事件接口
    │   │   ├── OneBotBaseEvent.java       # 事件基类
    │   │   ├── message/                   # 消息事件
    │   │   │   ├── OneBotMessageEvent.java
    │   │   │   ├── OneBotGroupMessageEvent.java
    │   │   │   └── OneBotPrivateMessageEvent.java
    │   │   ├── meta/                      # 元事件
    │   │   │   ├── OneBotMetaEvent.java
    │   │   │   ├── OneBotHeartbeatEvent.java
    │   │   │   └── OneBotLifecycleEvent.java
    │   │   └── notice/                    # 通知事件
    │   │       ├── OneBotNoticeEvent.java
    │   │       ├── OneBotGroupBanEvent.java
    │   │       ├── OneBotGroupDecreaseEvent.java
    │   │       ├── OneBotGroupIncreaseEvent.java
    │   │       ├── OneBotGroupRecallEvent.java
    │   │       ├── OneBotEssenceEvent.java
    │   │       └── PokeEvent.java
    │   ├── segment/                       # 消息段类型
    │   │   ├── OneBotMessageSegment.java  # 消息段基类
    │   │   ├── TextSegment.java           # 文本
    │   │   ├── ImageSegment.java          # 图片
    │   │   ├── AtSegment.java             # @
    │   │   ├── ReplySegment.java          # 回复
    │   │   ├── FaceSegment.java           # 表情
    │   │   ├── RecordSegment.java         # 语音
    │   │   ├── VideoSegment.java          # 视频
    │   │   ├── FileSegment.java           # 文件
    │   │   ├── JsonSegment.java           # JSON
    │   │   ├── ForwardSegment.java        # 转发
    │   │   ├── NodeSegment.java           # 节点
    │   │   └── ... (其他消息段)
    │   └── vo/                            # 值对象
    │       ├── OneBotConfigVO.java        # 配置 VO
    │       ├── LoginInfo.java             # 登录信息
    │       ├── GroupInfo.java             # 群信息
    │       ├── GroupMemberInfo.java       # 群成员信息
    │       ├── FriendInfo.java            # 好友信息
    │       ├── MessageId.java             # 消息 ID
    │       ├── MessageInfo.java           # 消息信息
    │       ├── FileInfo.java              # 文件信息
    │       └── ... (其他 VO)
    ├── service/                           # 服务层
    │   ├── OneBotConfigService.java       # 配置服务接口
    │   └── impl/
    │       └── OneBotConfigServiceImpl.java
    ├── util/                              # 工具类（单数，只有一条）
    │   └── ConnectionUrlUtil.java         # WS 连接 URL 解析
    └── utils/                             # 工具类（复数）
        ├── ChatEnvelope.java              # 信封模板 [时间] 名字(qq): 正文 [消息#id]
        ├── MessageLexer.java              # segment ↔ 模型字面格式的双向转换（含位置/卡片渲染）
        ├── MessageLexerException.java     # 字面格式非法
        ├── OneBotMessageParser.java       # 消息解析器（@Component：落库 parse_message + 引用/转发展开）
        └── OneBotMessageFileProcessor.java # 文件处理器（@Component：下载落库 + VLM 描述）
```

> ⚠️ `util/`（单数）与 `utils/`（复数）**只差一个字母但内容不同**，`utils/` 里还混着两个带 Spring 注解的业务组件
> （`OneBotMessageParser`、`OneBotMessageFileProcessor`）而不是纯工具。这是历史遗留，暂时照实记录，动它需要单独一次重构。

## 核心组件

### 平台类型 (PlatformType)

定义支持的平台类型枚举：

```java
@Getter
public enum PlatformType {
    ONEBOT_QQ("onebot_qq", "OneBot QQ", "OneBot 协议 QQ 机器人连接");

    private final String code;
    private final String name;
    private final String description;

    PlatformType(String code, String name, String description) {
        this.code = code;
        this.name = name;
        this.description = description;
    }

    public static PlatformType fromCode(String code) { ... }
}
```

### 协议适配器接口 (WebSocketProtocolAdapter)

定义 WebSocket 协议适配器的标准接口：

```java
public interface WebSocketProtocolAdapter {
    String getProtocolType();                                    // 返回协议类型标识
    void onMessage(ConnectionSession session, String message);   // 处理收到的消息
    void onConnect(ConnectionSession session);                   // 连接建立回调
    void onDisconnect(ConnectionSession session);                // 连接断开回调
}
```

### OneBot 消息处理器 (OneBotMessageHandler)

OneBot 协议的核心处理器，实现 `WebSocketProtocolAdapter` 接口：

```java
@Component
public class OneBotMessageHandler implements WebSocketProtocolAdapter {

    private static final String PROTOCOL_TYPE = "onebot_qq";

    @Override
    public String getProtocolType() {
        return PROTOCOL_TYPE;
    }

    @Override
    public void onMessage(ConnectionSession session, String message) {
        // 1. 处理 Echo 响应（API 调用的异步转同步）
        if (echoHandler.handleEcho(message)) {
            return;
        }

        // 2. 解析事件
        OneBotEvent event = OneBotEventParser.parse(message);

        // 3. 处理心跳事件
        if (event instanceof OneBotHeartbeatEvent heartbeat) {
            sessionManager.updateHeartbeat(session, heartbeat.getInterval());
            return;
        }

        // 4. 处理生命周期事件
        if (event instanceof OneBotLifecycleEvent lifecycle) {
            sessionManager.registerSelfId(session, event.getSelfId());
            return;
        }

        // 5. 发布事件到事件总线
        eventPublisher.publish(event);
    }

    @Override
    public void onConnect(ConnectionSession session) {
        sessionManager.registerSession(session);
    }

    @Override
    public void onDisconnect(ConnectionSession session) {
        sessionManager.removeSession(session);
    }
}
```

### Echo 响应处理器 (OneBotEchoHandler)

实现 API 调用的异步转同步机制：

```java
@Component
public class OneBotEchoHandler {

    private final Map<String, CompletableFuture<OneBotApiResponse>> pendingRequests = new ConcurrentHashMap<>();

    // 注册等待中的请求
    public CompletableFuture<OneBotApiResponse> register(String echo) {
        CompletableFuture<OneBotApiResponse> future = new CompletableFuture<>();
        pendingRequests.put(echo, future);
        return future;
    }

    // 处理收到的 Echo 响应
    public boolean handleEcho(String jsonRaw) {
        JsonNode node = objectMapper.readTree(jsonRaw);
        if (node.has("echo")) {
            String echo = node.get("echo").asText();
            CompletableFuture<OneBotApiResponse> future = pendingRequests.remove(echo);
            if (future != null) {
                OneBotApiResponse response = objectMapper.treeToValue(node, OneBotApiResponse.class);
                future.complete(response);
                return true;
            }
        }
        return false;
    }

    // 等待响应（带超时）
    public OneBotApiResponse waitForResponse(String echo, long timeout, TimeUnit unit) {
        return pendingRequests.get(echo).get(timeout, unit);
    }
}
```

### 事件模型

#### 事件接口与基类

```java
// 事件接口
public interface OneBotEvent {
    long getTime();
    long getSelfId();
    String getPostType();
}

// 事件基类（使用自定义反序列化器）
@Data
@JsonDeserialize(using = OneBotEventDeserializer.class)
public abstract class OneBotBaseEvent implements OneBotEvent {
    private long time;

    @JsonProperty("self_id")
    private long selfId;

    @JsonProperty("post_type")
    private String postType;
}
```

#### 事件反序列化器

根据 `post_type` 字段自动路由到对应的事件类：

```java
public class OneBotEventDeserializer extends StdDeserializer<OneBotEvent> {

    @Override
    public OneBotEvent deserialize(JsonParser p, DeserializationContext ctxt) {
        ObjectMapper mapper = (ObjectMapper) p.getCodec();
        JsonNode node = mapper.readTree(p);

        if (!node.has("post_type")) {
            return null;
        }

        String postType = node.get("post_type").asText();
        Class<? extends OneBotEvent> targetClass = null;

        switch (postType) {
            case "message":
                // 根据 message_type 路由到群消息或私聊消息
            case "meta_event":
                // 根据 meta_event_type 路由到心跳或生命周期事件
            case "notice":
                // 根据 notice_type 路由到具体通知事件
        }

        if (targetClass != null) {
            return mapper.treeToValue(node, targetClass);
        }
        return null;
    }
}
```

#### 支持的事件类型

| 事件类 | post_type | 说明 |
|--------|-----------|------|
| `OneBotGroupMessageEvent` | message | 群消息事件 |
| `OneBotPrivateMessageEvent` | message | 私聊消息事件 |
| `OneBotHeartbeatEvent` | meta_event | 心跳事件 |
| `OneBotLifecycleEvent` | meta_event | 生命周期事件 |
| `OneBotGroupBanEvent` | notice | 群禁言事件 |
| `OneBotGroupDecreaseEvent` | notice | 群成员减少事件 |
| `OneBotGroupIncreaseEvent` | notice | 群成员增加事件 |
| `OneBotGroupRecallEvent` | notice | 群消息撤回事件 |
| `OneBotEssenceEvent` | notice | 精华消息事件 |
| `PokeEvent` | notice | 戳一戳事件 |

### 消息段 (Message Segment)

支持的消息段类型（使用 Jackson 多态序列化）：

```java
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "type",
    visible = true
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = TextSegment.class, name = "text"),
    @JsonSubTypes.Type(value = ImageSegment.class, name = "image"),
    @JsonSubTypes.Type(value = AtSegment.class, name = "at"),
    @JsonSubTypes.Type(value = ReplySegment.class, name = "reply"),
    @JsonSubTypes.Type(value = FaceSegment.class, name = "face"),
    @JsonSubTypes.Type(value = RecordSegment.class, name = "record"),
    @JsonSubTypes.Type(value = VideoSegment.class, name = "video"),
    @JsonSubTypes.Type(value = FileSegment.class, name = "file"),
    @JsonSubTypes.Type(value = JsonSegment.class, name = "json"),
    @JsonSubTypes.Type(value = ForwardSegment.class, name = "forward"),
    @JsonSubTypes.Type(value = NodeSegment.class, name = "node"),
    @JsonSubTypes.Type(value = PokeSegment.class, name = "poke"),
    @JsonSubTypes.Type(value = LocationSegment.class, name = "location"),
    // ... 更多类型
})
public abstract class OneBotMessageSegment {
    private String type;
}
```

### 消息构建器 (OneBotMessageBuilder)

流式 API 构建消息链：

```java
// 构建群消息
OneBotSendingMessage message = OneBotMessageBuilder.create()
    .text("Hello ")
    .at("123456789")
    .text("!")
    .image("http://example.com/image.jpg", false)
    .build();

// 回复消息
OneBotSendingMessage reply = OneBotMessageBuilder.create()
    .reply(messageId)
    .text("收到!")
    .build();

// 发送语音（只能单独使用）
OneBotSendingMessage record = OneBotMessageBuilder.create()
    .record("http://example.com/voice.amr")
    .build();

// 合并转发
OneBotSendingMessage forward = OneBotMessageBuilder.create()
    .customNode("123456", "用户A", "消息内容1")
    .customNode("789012", "用户B", "消息内容2")
    .build();
```

### API 服务 (OneBotApiService)

封装 OneBot API 调用：

```java
@Service
public class OneBotApiService {

    // 发送消息
    public MessageId sendPrivateMsg(long botId, long userId, OneBotSendingMessage message);
    public MessageId sendGroupMsg(long botId, long groupId, OneBotSendingMessage message);

    // 群管理
    public void setGroupBan(long botId, long groupId, long userId, long duration);
    public void setGroupWholeBan(long botId, long groupId, boolean enable);
    public void setGroupAdmin(long botId, long groupId, long userId, boolean enable);
    public void setGroupKick(long botId, long groupId, long userId, boolean rejectAddRequest);

    // 获取信息
    public LoginInfo getLoginInfo(long botId);
    public GroupInfo getGroupInfo(long botId, long groupId, boolean noCache);
    public List<GroupInfo> getGroupList(long botId);
    public GroupMemberInfo getGroupMemberInfo(long botId, long groupId, long userId, boolean noCache);
    public List<GroupMemberInfo> getGroupMemberList(long botId, long groupId);
    public List<FriendInfo> getFriendList(long botId);

    // 其他操作
    public void deleteMsg(long botId, int messageId);
    public void sendLike(long botId, long userId, int times);
    public void sendGroupPoke(long botId, long groupId, long userId);

    // 通用 API 调用
    public <T> T callApi(long botId, String action, Map<String, Object> params, Class<T> responseType);
    public <T> List<T> callApiList(long botId, String action, Map<String, Object> params, Class<T> elementType);
    public void callApiVoid(long botId, String action, Map<String, Object> params);
}
```

### 配置管理 (OneBotConfigService)

管理 OneBot 连接配置和服务器生命周期：

```java
public interface OneBotConfigService extends IService<OneBotConfig> {
    List<OneBotConfigVO> listAll();
    OneBotConfigVO getById(Long id);
    Long createConfig(CreateOneBotConfigRequest request);
    void updateConfig(UpdateOneBotConfigRequest request);
    void deleteConfig(Long id);
    void startServer(Long id);
    void stopServer(Long id);
    void setEnabled(Long id, boolean enabled);
    OneBotConfigVO getServerStatus(Long id);
    List<OneBotConfig> listEnabled();
    void startAllEnabledServers();
}
```

### 消息解析器 (MessageParser)

将消息段列表转换为可读文本：

```java
@Component
public class MessageParser {

    // 解析消息段列表
    public String parseMessage(List<OneBotMessageSegment> segments, Long botId);

    // 支持的消息段类型
    private String parseTextSegment(TextSegment segment);      // 文本
    private String parseAtSegment(AtSegment segment);          // @
    private String parseFaceSegment(FaceSegment segment);      // 表情
    private String parseImageSegment(ImageSegment segment);    // 图片
    private String parseFileSegment(FileSegment segment);      // 文件
    private String parseVideoSegment(VideoSegment segment);    // 视频
    private String parseRecordSegment(RecordSegment segment);  // 语音
    private String parseJsonSegment(JsonSegment segment);      // JSON
    private String parseForwardSegment(ForwardSegment segment, Long botId);  // 转发
    private String parseReplySegment(ReplySegment segment, Long botId);      // 回复
    // 位置：直接复用 MessageLexer.renderLocationText，保证落库的 parse_message 与信封渲染同格式
}
```

### 历史消息（群 / 私聊）

`get_group_msg_history` / `get_friend_msg_history` 两个接口逐字段同构，只有目标参数不同（`group_id` / `user_id`），
都返回 `data.messages`（OB11Message 数组）。

```java
// MangoOneBotApi（便捷重载）
List<OneBotGroupMessageEvent>   getGroupMsgHistory(long botId, long groupId, int count);
List<OneBotPrivateMessageEvent> getFriendMsgHistory(long botId, long userId, int count);

// 完整签名：messageSeq / count / reverseOrder
List<OneBotGroupMessageEvent>   getGroupMsgHistory(long botId, long groupId, Integer messageSeq, int count, boolean reverseOrder);
List<OneBotPrivateMessageEvent> getFriendMsgHistory(long botId, long userId, Integer messageSeq, int count, boolean reverseOrder);
```

> ⚠️ **`message_seq` 的方向怪癖**：不传时从最新往回取；一旦传了具体序号，NapCat 实际是从该条**往后**取，
> 而不是往回翻页；`reverse_order` 只翻转返回列表、不改变查询方向（社区反馈，非官方文档）。
> 想往前翻历史，目前只能「不传 messageSeq + 加大 count」。

### 发送位置（只能走 Ark 卡片）

```java
// 群聊 / 私聊
MangoOneBotApi.sendGroupLocation(botId, groupId, lat, lng, name, address);
MangoOneBotApi.sendPrivateLocation(botId, userId, lat, lng, name, address);
```

**不要用 OneBot 的 `location` 段发位置。** 协议上它有定义（`{"type":"location","data":{"lat","lon"}}`），
NapCat 的接口文档也认，但实测发出去 QQ 不认：NapCat 4.15.1 把它转成 `ElementType=28`，自己日志里标
「未实现」，客户端显示「该消息类型暂不支持查看」。

真人发的位置，在 NapCat 里**根本不是 `location` 段**，而是腾讯地图的 Ark 卡片
（`app=com.tencent.map`、`view=LocationShare`、坐标在 `meta["Location.Search"]` 里）。
发送路径就照这个结构拼（`LocationCard`），字段是逐字段从真实消息抄的，两个易错点：

- 坐标键是 **`lng`**，不是 `location` 段里的 `lon`；
- `meta` 的键是带点的字面量 **`"Location.Search"`**，不是嵌套对象。

`LocationSegment` 仍然保留，但**只用于接收**——别的 OneBot 实现（go-cqhttp / Lagrange）会发这种段，
渲染成 `[位置: 标题 (纬度,经度)]`。收到的腾讯地图 Ark（`json` 段）也渲染成同一种形态。
```

---

## 扩展新平台指南

以下步骤说明如何为 MangoBot 添加新的平台协议支持。

### 第一步：添加平台类型枚举

在 [PlatformType.java](model/enums/PlatformType.java) 中添加新平台：

```java
@Getter
public enum PlatformType {
    ONEBOT_QQ("onebot_qq", "OneBot QQ", "OneBot 协议 QQ 机器人连接"),
    NEW_PLATFORM("new_platform", "新平台", "新平台协议描述");  // 新增

    // ...
}
```

### 第二步：创建目录结构

在 `adapter` 下创建对应的模块：

```
adapter/
└── new_platform/                         # 新平台实现
    ├── controller/
    │   ├── NewPlatformConfigController.java
    │   └── NewPlatformApiController.java
    ├── handler/
    │   ├── echo/
    │   │   ├── NewPlatformEchoHandler.java
    │   │   └── NewPlatformApiResponse.java
    │   ├── inbound/
    │   │   ├── json_to_event/
    │   │   │   ├── NewPlatformEventParser.java
    │   │   │   └── NewPlatformEventDeserializer.java
    │   │   └── receive_websocket_message/
    │   │       └── NewPlatformMessageHandler.java
    │   └── outbound/
    │       ├── send/
    │       │   └── NewPlatformApiService.java
    │       └── build_sending_message/
    │           ├── NewPlatformMessageBuilder.java
    │           └── NewPlatformSendingMessage.java
    ├── model/
    │   ├── domain/
    │   │   └── NewPlatformConfig.java
    │   ├── dto/
    │   │   ├── CreateNewPlatformConfigRequest.java
    │   │   └── UpdateNewPlatformConfigRequest.java
    │   ├── event/
    │   │   ├── NewPlatformEvent.java
    │   │   ├── NewPlatformBaseEvent.java
    │   │   └── NewPlatformMessageEvent.java
    │   ├── segment/
    │   │   └── NewPlatformMessageSegment.java
    │   └── vo/
    │       └── NewPlatformConfigVO.java
    ├── service/
    │   ├── NewPlatformConfigService.java
    │   └── impl/
    │       └── NewPlatformConfigServiceImpl.java
    └── utils/
        └── MessageParser.java
```

### 第三步：实现协议适配器

创建消息处理器实现 `WebSocketProtocolAdapter` 接口：

```java
@Component
public class NewPlatformMessageHandler implements WebSocketProtocolAdapter {

    private static final String PROTOCOL_TYPE = "new_platform";

    private final MangoEventPublisher eventPublisher;
    private final ConnectionSessionManager sessionManager;

    public NewPlatformMessageHandler(MangoEventPublisher eventPublisher,
                                     ConnectionSessionManager sessionManager) {
        this.eventPublisher = eventPublisher;
        this.sessionManager = sessionManager;
    }

    @Override
    public String getProtocolType() {
        return PROTOCOL_TYPE;
    }

    @Override
    public void onMessage(ConnectionSession session, String message) {
        // 1. 处理 Echo 响应（如果需要 API 调用）
        if (echoHandler.handleEcho(message)) {
            return;
        }

        // 2. 解析事件
        NewPlatformEvent event = NewPlatformEventParser.parse(message);

        // 3. 处理特殊事件（如心跳、生命周期）
        if (event instanceof NewPlatformHeartbeatEvent heartbeat) {
            sessionManager.updateHeartbeat(session, heartbeat.getInterval());
            return;
        }

        // 4. 发布事件到事件总线
        eventPublisher.publish(event);
    }

    @Override
    public void onConnect(ConnectionSession session) {
        log.info("新平台连接建立: {}", session.getRemoteAddress());
        sessionManager.registerSession(session);
    }

    @Override
    public void onDisconnect(ConnectionSession session) {
        log.info("新平台连接断开");
        sessionManager.removeSession(session);
    }
}
```

### 第四步：定义事件模型

创建事件接口和具体事件类：

```java
// 事件接口
public interface NewPlatformEvent {
    long getTime();
    String getEventType();
}

// 事件基类（使用自定义反序列化器）
@Data
@JsonDeserialize(using = NewPlatformEventDeserializer.class)
public abstract class NewPlatformBaseEvent implements NewPlatformEvent {
    private long time;
    private String eventType;
}

// 消息事件
@Data
@EqualsAndHashCode(callSuper = true)
public class NewPlatformMessageEvent extends NewPlatformBaseEvent {
    private long senderId;
    private String content;
    private String targetType;  // "group" / "private"
    private long targetId;
}
```

### 第五步：实现事件反序列化器

```java
public class NewPlatformEventDeserializer extends StdDeserializer<NewPlatformEvent> {

    @Override
    public NewPlatformEvent deserialize(JsonParser p, DeserializationContext ctxt) {
        ObjectMapper mapper = (ObjectMapper) p.getCodec();
        JsonNode node = mapper.readTree(p);

        String eventType = node.get("event_type").asText();
        Class<? extends NewPlatformEvent> targetClass = switch (eventType) {
            case "message" -> NewPlatformMessageEvent.class;
            case "heartbeat" -> NewPlatformHeartbeatEvent.class;
            // ... 其他事件类型
            default -> null;
        };

        if (targetClass != null) {
            return mapper.treeToValue(node, targetClass);
        }
        return null;
    }
}
```

### 第六步：实现 API 服务（可选）

如果平台需要主动调用 API：

```java
@Service
public class NewPlatformApiService {

    private final ConnectionSessionManager sessionManager;
    private final ObjectMapper objectMapper;

    public void sendMessage(long botId, long targetId, String content) {
        ConnectionSession session = sessionManager.getSessionBySelfId(botId);
        if (session == null || !session.isConnected()) {
            throw new BusinessException("机器人未连接");
        }

        // 构建 API 请求
        Map<String, Object> request = new HashMap<>();
        request.put("action", "send_message");
        request.put("target_id", targetId);
        request.put("content", content);

        // 发送请求
        String json = objectMapper.writeValueAsString(request);
        session.getConnection().send(json);
    }
}
```

### 第七步：创建配置管理

参考 `OneBotConfigService` 实现配置管理：

1. **实体类** - 存储连接配置（host、port、token 等）
2. **Service** - 配置的 CRUD 和服务器生命周期管理
3. **Controller** - REST API 接口

### 第八步：注册事件监听

在插件或业务代码中监听新平台事件：

```java
@Component
public class NewPlatformEventListener {

    @MangoBotEventListener
    public boolean onMessage(NewPlatformMessageEvent event) {
        // 处理消息
        return false;  // 返回 false 继续传递
    }
}
```

---

## 关键依赖

扩展新平台时需要依赖以下组件：

| 组件 | 用途 |
|------|------|
| `MangoEventPublisher` | 发布事件到事件总线 |
| `ConnectionSessionManager` | 管理 WebSocket 会话 |
| `WebSocketServerManager` | 管理 WebSocket 服务器生命周期 |

## 注意事项

1. **协议类型标识**：`getProtocolType()` 返回值必须与 `PlatformType.code` 一致
2. **线程安全**：消息处理可能在多线程环境中执行，注意并发安全
3. **异常处理**：消息解析失败时应记录日志，避免影响其他消息处理
4. **会话管理**：在 `onConnect` 和 `onDisconnect` 中正确注册/移除会话
5. **事件反序列化**：使用自定义 `JsonDeserializer` 实现多态事件解析
6. **消息段类型**：如需支持新的消息段类型，需在基类上添加 `@JsonSubTypes.Type` 注解
7. **Echo 处理**：如果需要 API 调用能力，必须实现 Echo 响应处理机制
