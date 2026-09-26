package io.github.mangomaner.mangobot.module.agent.sim.harness;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 应用内的模拟 NapCat 客户端：连上本进程自己的 mock 服务端（{@code onebot_config} 里 name=mock，
 * 默认 8085），扮演"被测 bot 所在的群"。
 *
 * <p><b>不能靠"在发送出口拦一道、只记不发"来记录</b>：那样形状与生产差了一截，绕过了
 * {@code OneBotApiService} 的 echo 往返、也绕过了落库。这里反过来——bot 的回复<b>真的</b>按
 * OneBot 协议发出去，从 mock 服务端回到这个客户端，记录的是既成事实。副作用是好的：
 * bot 的话会经 {@code saveGroupMessage} 落进 {@code group_messages}，
 * 于是"群聊页里能实时看到模拟群的对话"成了一个免费的验证面。
 *
 * <p>三件事缺一不可，都是协议要求：
 * <ul>
 *   <li>连上先发 {@code lifecycle}（带 {@code self_id}）：服务端凭它建
 *       {@code sessionsBySelfId} 索引，<b>不发就永远发不出消息</b>（出站按 self_id 找连接）。</li>
 *   <li>每 30 秒发一次 {@code heartbeat}：服务端 {@code checkHeartbeats} 在
 *       {@code interval*3}（默认 180 秒）没收到心跳就关连接，一场模拟要跑好几分钟。</li>
 *   <li>对每条带 {@code echo} 的请求原样回 {@code retcode:0} + {@code data.message_id}：
 *       服务端在 echo 上同步等最多 60 秒，不回就是一次 60 秒卡死外加"这条没落库"。</li>
 * </ul>
 *
 * <p>心跳与 lifecycle 都由这个类自己发；消息帧由调用方调 {@link #sendRaw}。
 */
@Slf4j
public class SimOneBotClient extends WebSocketClient {

    /** 心跳间隔；必须远小于服务端的超时（{@code heartbeatInterval*3}，默认 180 秒）。 */
    private static final long HEARTBEAT_INTERVAL_SECONDS = 30L;

    /** 心跳帧里自报的 interval（毫秒），服务端据此设超时——报 60 秒是 OneBot 的常规值。 */
    private static final long HEARTBEAT_INTERVAL_MILLIS = 60_000L;

    private final long selfId;
    private final SimImageIndex imageIndex;

    /** 出站帧还原出的发送，按到达顺序。多线程访问（WS 读线程写、驱动线程读）。 */
    private final List<SimSend> recorded = Collections.synchronizedList(new ArrayList<>());

    /** 回给服务端的 {@code message_id}；只要是个递增整数即可。 */
    private final AtomicInteger nextMessageId = new AtomicInteger(1);

    private volatile ScheduledExecutorService heartbeats;
    private volatile boolean closing;

    public SimOneBotClient(URI serverUri, long selfId, SimImageIndex imageIndex) {
        super(serverUri);
        this.selfId = selfId;
        this.imageIndex = imageIndex;
        // 关掉客户端自带的掉线检测（它靠 ping/pong 计时，会和我们的显式心跳叠成两套）；
        // 连接是否还活着由我们自己的心跳帧向服务端证明。
        setConnectionLostTimeout(0);
    }

    @Override
    public void onOpen(ServerHandshake handshake) {
        log.info("模拟 OneBot 客户端已连接：selfId={} uri={}", selfId, getURI());
        sendRaw(lifecycleFrame());
        ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "sim-onebot-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
        timer.scheduleWithFixedDelay(this::sendHeartbeat,
                HEARTBEAT_INTERVAL_SECONDS, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);
        heartbeats = timer;
    }

    @Override
    public void onMessage(String raw) {
        JsonNode node;
        try {
            node = SimJson.MAPPER.readTree(raw);
        } catch (Exception e) {
            log.warn("模拟客户端收到无法解析的帧，已忽略：{}", raw, e);
            return;
        }
        JsonNode echo = node.get("echo");
        if (echo == null || echo.isNull()) {
            // 服务端除了应答不会有别的东西推过来；空 echo 的帧当噪声处理
            log.debug("模拟客户端收到非应答帧，已忽略：{}", raw);
            return;
        }

        List<SimSend> sends = interpret(node.path("action").asText(""), node.path("params"));
        if (!sends.isEmpty()) {
            recorded.addAll(sends);
        }
        // 一定要回：服务端在 echo 上同步等 60 秒，不回就卡一轮，且这条消息不会落库
        sendRaw(echoResponse(echo.asText()));
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        if (!closing) {
            log.warn("模拟 OneBot 连接被关闭：code={} reason={} remote={}", code, reason, remote);
        }
        stopHeartbeats();
    }

    @Override
    public void onError(Exception ex) {
        log.error("模拟 OneBot 客户端出错", ex);
    }

    /** 发一帧原始 JSON（消息帧由调用方构造）。 */
    public void sendRaw(String json) {
        if (isOpen()) {
            send(json);
        } else {
            log.warn("模拟 OneBot 连接未打开，这一帧没有发出去：{}", json);
        }
    }

    /**
     * 取走并清空已记录的发送。
     *
     * <p>与 {@link io.github.mangomaner.mangobot.module.agent.chat.TurnSignal} 的配合是<b>有序</b>的：
     * 发送工具要等 echo 回来才返回，而记录发生在回 echo 之前，所以"记录"严格早于"这一轮的结束信号"。
     * 等到信号再取，取到的必然属于这一轮。
     */
    public List<SimSend> drainSends() {
        synchronized (recorded) {
            List<SimSend> taken = List.copyOf(recorded);
            recorded.clear();
            return taken;
        }
    }

    /** 断开并停掉心跳线程；收尾必调，否则这个客户端线程会一直挂着。 */
    public void shutdown() {
        closing = true;
        stopHeartbeats();
        try {
            closeBlocking();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ========================================================================
    // 内部
    // ========================================================================

    private void stopHeartbeats() {
        ScheduledExecutorService timer = heartbeats;
        if (timer != null) {
            timer.shutdownNow();
            heartbeats = null;
        }
    }

    private void sendHeartbeat() {
        ObjectNode root = SimJson.MAPPER.createObjectNode();
        root.put("post_type", "meta_event");
        root.put("meta_event_type", "heartbeat");
        root.put("sub_type", "heartbeat");
        root.put("self_id", selfId);
        root.put("time", System.currentTimeMillis() / 1000L);
        root.put("interval", HEARTBEAT_INTERVAL_MILLIS);
        root.putObject("status").put("online", true);
        sendRaw(root.toString());
    }

    private String lifecycleFrame() {
        ObjectNode root = SimJson.MAPPER.createObjectNode();
        root.put("post_type", "meta_event");
        root.put("meta_event_type", "lifecycle");
        root.put("sub_type", "connect");
        root.put("self_id", selfId);
        root.put("time", System.currentTimeMillis() / 1000L);
        return root.toString();
    }

    private String echoResponse(String echo) {
        ObjectNode root = SimJson.MAPPER.createObjectNode();
        root.put("status", "ok");
        root.put("retcode", 0);
        root.put("echo", echo);
        root.putObject("data").put("message_id", nextMessageId.getAndIncrement());
        return root.toString();
    }

    /**
     * 把一条出站请求还原成"bot 发了什么"。
     *
     * <p>只认 {@code send_group_msg} / {@code send_private_msg}：别的动作（{@code get_*} 之类）不是
     * bot 在说话，记进来只会污染观测。返回空表表示"这一帧不算一次发言"。
     *
     * <p>{@code at} 段还原成 {@code [@qq]} 而不是丢掉：协议里它是独立段、不在 text 里，
     * 丢掉就看不出 bot 有没有 @ 人。
     *
     * <p>图片段的分支：{@code sub_type} 为 1/11 是表情包（发出口是 {@code send_meme}），否则是复读
     * （{@code send_image}）。<b>表情包的关键词在路上不存在</b>——帧里只有字节和 sub_type——所以
     * {@code meme} 的 payload 只能是反查出来的 fileId，不是模型给的关键词。
     */
    private List<SimSend> interpret(String action, JsonNode params) {
        if (!"send_group_msg".equals(action) && !"send_private_msg".equals(action)) {
            return List.of();
        }
        JsonNode message = params.get("message");
        if (message == null || !message.isArray()) {
            return List.of();
        }

        List<SimSend> sends = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        // reply 段是引用而非内容，但它必须留下来：不记就验不出"模型有没有在乱引用"。
        // 协议里 reply 排在最前面，所以先攒着，落到这一帧产生的第一条发言上。
        String replyToId = null;
        for (JsonNode segment : message) {
            switch (segment.path("type").asText("")) {
                case "reply" -> {
                    String id = segment.path("data").path("id").asText("");
                    replyToId = id.isBlank() ? null : id;
                }
                case "text" -> text.append(segment.path("data").path("text").asText(""));
                case "at" -> text.append("[@").append(segment.path("data").path("qq").asText("")).append(']');
                case "image" -> {
                    flushText(text, sends, replyToId);
                    sends.add(imageSend(segment.path("data"), replyToId));
                }
                // 其余段本项目不会发，忽略
                default -> {
                }
            }
        }
        flushText(text, sends, replyToId);
        return sends;
    }

    private static void flushText(StringBuilder buffer, List<SimSend> sends, String replyToId) {
        if (buffer.length() > 0) {
            sends.add(new SimSend("text", buffer.toString(), replyToId));
            buffer.setLength(0);
        }
    }

    private SimSend imageSend(JsonNode data, String replyToId) {
        String file = data.path("file").asText("");
        boolean meme = isMemeSubType(data.path("sub_type").asInt(0));

        String fileId = null;
        if (file.startsWith("base64://")) {
            fileId = imageIndex.fileIdOf(decodeBase64(file.substring("base64://".length())));
        } else if (!file.isBlank()) {
            // 有些实现直接给 fileId/本地路径而没有字节；这种就原样当 id 用
            fileId = file;
        }

        if (fileId == null) {
            log.warn("出站图片认不出是哪张（字节不在本轮引用过的图里）：subType={} bytes={}",
                    meme ? "meme" : "image", file.length());
            fileId = "<未识别图片>";
        }
        return new SimSend(meme ? "meme" : "image", fileId, replyToId);
    }

    private static boolean isMemeSubType(int subType) {
        return subType == 1 || subType == 11;
    }

    private static byte[] decodeBase64(String encoded) {
        try {
            return Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException e) {
            log.warn("出站图片的 base64 解不开，无法反查 fileId", e);
            return null;
        }
    }
}
