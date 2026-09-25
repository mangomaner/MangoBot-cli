package io.github.mangomaner.mangobot.module.agent.capability.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.mangomaner.mangobot.annotation.MangoToolMethod;
import io.github.mangomaner.mangobot.annotation.MangoToolParam;
import io.github.mangomaner.mangobot.module.agent.model.domain.AgentJavaToolConfig;
import io.github.mangomaner.mangobot.module.agent.model.enums.SessionSource;
import io.github.mangomaner.mangobot.module.agent.service.AgentJavaToolConfigService;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStatelessServerTransport;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 把「启用中的 Java 工具」经 MCP 暴露给 claude 调用的 MCP server。
 *
 * <p>方向是定的：<b>MangoBot 是宿主，claude 是我们的子进程</b>，所以只能由本进程起 MCP server，
 * claude 用 {@code --mcp-config} 连回来（HTTP，挂在同一台机器的 {@code server.port} 上）。
 * claude 侧那份配置由 {@code ClaudeRuntimeManager} 生成。
 *
 * <h3>为什么每个来源一个 server</h3>
 * <p>一个 MCP server 的工具清单在 build 时定死，同一个 server 做不到"不同会话看到不同清单"。而
 * 「能力页」上 Java 工具是有"启用来源"这一栏的，要让它真的生效，只能按来源各起一个
 * （{@code /mcp/web}、{@code /mcp/group}、{@code /mcp/private}），各自只挂该来源可用的工具。
 * 副产品：发送工具只对群聊/私聊可用，web 的清单里天然没有它。
 *
 * <h3>暴露规则</h3>
 * <ul>
 *   <li>只暴露 {@code agent_java_tool_config.enabled = true} 的工具；类加载不起来（插件场景）跳过并告警</li>
 *   <li>一个 Java 工具类里，<b>标了 {@link MangoToolMethod} 的 public 方法各自成为一个 MCP tool</b>，
 *       名字默认 {@code <工具名>_<方法名>}；参数由 {@link MangoToolParam} 生成 inputSchema</li>
 *   <li><b>按来源过滤</b>：{@code available_list} 与 {@code enabled_list} 都含该来源才挂上去</li>
 * </ul>
 *
 * <h3>会话身份</h3>
 * <p>同一个 server 服务该来源下的所有会话，所以"是谁在调我"挂在每次请求的
 * {@value #SESSION_HEADER} 头上：transport 的 contextExtractor 把它取进 {@link McpTransportContext}，
 * {@link ExposedTool} 的 callHandler 再转成 {@link MangoToolContext}（工具对象拿不到 transport ctx）。
 *
 * <p>Servlet 实例在 {@link #transport(SessionSource)} 里建、由 {@code McpServletConfig} 注册到容器；
 * 真正的工具挂载在 {@link #init()}——项目约定业务初始化不用 {@code @PostConstruct}，
 * 由 {@code MangoBotApplication.main()} 显式调用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JavaToolMcpServer {

    /** claude 侧 {@code --mcp-config} 要连的路径前缀，后面接来源标识 */
    public static final String MESSAGE_ENDPOINT_PREFIX = "/mcp";

    /** 写进 claude 那份 mcp.json 的 server 名 */
    public static final String MCP_SERVER_NAME = "mangobot";

    /** claude 侧带会话身份的请求头；服务端在这里取回来 */
    public static final String SESSION_HEADER = "X-Mango-Session";

    /** {@link McpTransportContext} 里存会话身份的键 */
    private static final String CTX_SESSION_KEY = "session";

    private static final String SERVER_VERSION = "0.1.0";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AgentJavaToolConfigService toolConfigService;
    private final JavaToolLoader javaToolLoader;

    private final McpJsonMapper jsonMapper = new JacksonMcpJsonMapper(MAPPER);

    /** 本进程监听端口；与 server.port 一致才能被同一台机器上的子进程连上。 */
    @Value("${server.port:8082}")
    private int port;

    /** 每个来源一套 transport + server + 已挂工具名。 */
    private final Map<SessionSource, SourceEndpoint> endpoints = new EnumMap<>(SessionSource.class);

    /**
     * 建三个来源的 MCP server 并按当前配置挂上工具。由 {@code MangoBotApplication.main()} 显式调用。
     */
    public void init() {
        rebuild();
        for (SessionSource source : SessionSource.values()) {
            log.info("Java 工具 MCP server 就绪：source={} url={} tools={}",
                    source.getSourceKey(), serverUrl(source), exposedToolCount(source));
        }
    }

    /** claude 子进程要连的地址（写进它那份 mcp.json）。 */
    public String serverUrl(SessionSource source) {
        return "http://127.0.0.1:" + port + messageEndpoint(source);
    }

    /** claude 侧 {@code --mcp-config} 里要配的路径。 */
    public static String messageEndpoint(SessionSource source) {
        return MESSAGE_ENDPOINT_PREFIX + "/" + source.getSourceKey();
    }

    /** 该来源当前暴露的 MCP tool 数量；为 0 时不必把这个 server 写进 claude 的配置。 */
    public int exposedToolCount(SessionSource source) {
        return endpoint(source).exposedNames.size();
    }

    /**
     * 工具配置变了之后重新挂载（启用/禁用/注销/改来源）。没 init 过就先建起来。
     *
     * <p>挂了新工具也只是下次 spawn 的 claude 会话能看见；已经跑着的会话拿到的工具清单是它连接时的快照。
     */
    public synchronized void reload() {
        for (SessionSource source : SessionSource.values()) {
            SourceEndpoint endpoint = endpoint(source);
            if (endpoint.server == null) {
                rebuild(source);
                continue;
            }
            List<ExposedTool> fresh = collectTools(source);
            Set<String> freshNames = fresh.stream().map(ExposedTool::name)
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            for (String stale : endpoint.exposedNames) {
                if (!freshNames.contains(stale)) {
                    endpoint.server.removeTool(stale);
                    log.info("MCP tool 已下线：source={} tool={}", source.getSourceKey(), stale);
                }
            }
            for (ExposedTool tool : fresh) {
                if (endpoint.exposedNames.contains(tool.name())) {
                    continue;
                }
                endpoint.server.addTool(tool.spec());
                log.info("MCP tool 已上线：source={} tool={}", source.getSourceKey(), tool.name());
            }
            endpoint.exposedNames = Set.copyOf(freshNames);
        }
    }

    @PreDestroy
    public void close() {
        for (SourceEndpoint endpoint : endpoints.values()) {
            McpStatelessSyncServer server = endpoint.server;
            if (server != null) {
                server.close();
            }
        }
    }

    /**
     * 某来源的 servlet 实例（由 {@code McpServletConfig} 注册到容器）。
     *
     * <p>幂等：并发首调也只建一个。
     */
    public synchronized HttpServletStatelessServerTransport transport(SessionSource source) {
        return endpoint(source).transport(jsonMapper);
    }

    // ========================================================================
    // 内部
    // ========================================================================

    private SourceEndpoint endpoint(SessionSource source) {
        return endpoints.computeIfAbsent(source, SourceEndpoint::new);
    }

    private synchronized void rebuild() {
        for (SessionSource source : SessionSource.values()) {
            rebuild(source);
        }
    }

    private void rebuild(SessionSource source) {
        List<ExposedTool> tools = collectTools(source);
        SourceEndpoint endpoint = endpoint(source);
        McpServer.StatelessSyncSpecification spec = McpServer.sync(endpoint.transport(jsonMapper))
                .serverInfo(MCP_SERVER_NAME, SERVER_VERSION)
                .jsonMapper(jsonMapper)
                // 必须显式声明 tools 能力：一个工具都没有时 build() 不会带上它，
                // 之后 reload() 的 addTool 会抛 "Server must be configured with tool capabilities"
                .capabilities(McpSchema.ServerCapabilities.builder().tools(true).build())
                .tools(tools.stream().map(ExposedTool::spec).toList());

        endpoint.server = spec.build();
        endpoint.exposedNames = Set.copyOf(tools.stream().map(ExposedTool::name).toList());
    }

    /** 扫「该来源启用的工具配置 × 标了 {@link MangoToolMethod} 的方法」，逐个反射成 MCP tool。 */
    private List<ExposedTool> collectTools(SessionSource source) {
        List<ExposedTool> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        for (AgentJavaToolConfig config : toolConfigService.listEnabled()) {
            if (!supportsSource(config.getAvailableList(), source)
                    || !supportsSource(config.getEnabledList(), source)) {
                continue;
            }
            Optional<Object> loaded = javaToolLoader.loadTool(config);
            if (loaded.isEmpty()) {
                log.warn("Java 工具「{}」未暴露：实例加载失败，class={}",
                        config.getToolName(), config.getClassName());
                continue;
            }
            Object instance = loaded.get();
            String toolName = config.getToolName() != null && !config.getToolName().isBlank()
                    ? config.getToolName()
                    : instance.getClass().getSimpleName();

            for (Method method : instance.getClass().getMethods()) {
                MangoToolMethod annotation = method.getAnnotation(MangoToolMethod.class);
                if (annotation == null) {
                    continue;
                }
                ExposedTool tool = ExposedTool.of(toolName, config.getDescription(), instance, method, annotation, jsonMapper);
                if (tool == null) {
                    log.warn("Java 工具方法未暴露（名字非法）：{}#{}", config.getClassName(), method.getName());
                    continue;
                }
                if (!seen.add(tool.name())) {
                    log.warn("MCP tool 名字重复，跳过后一个：{}（{}#{}）",
                            tool.name(), config.getClassName(), method.getName());
                    continue;
                }
                result.add(tool);
            }
        }
        return result;
    }

    /** 来源列表 JSON 里是否含该来源；空或坏 JSON 按「不含」处理（宁可少挂也不要挂错）。 */
    private static boolean supportsSource(String json, SessionSource source) {
        if (json == null || json.isBlank()) {
            return false;
        }
        try {
            List<String> keys = MAPPER.readValue(json, new TypeReference<List<String>>() {
            });
            return keys.contains(source.getSourceKey());
        } catch (JsonProcessingException e) {
            log.warn("工具来源列表不是合法 JSON，按「不含任何来源」处理：{}", json);
            return false;
        }
    }

    /**
     * 一个来源的一整套运行时状态：transport（servlet 用的那个）、server、已挂工具名。
     */
    private static final class SourceEndpoint {

        private final SessionSource source;
        private volatile HttpServletStatelessServerTransport transport;
        private volatile McpStatelessSyncServer server;
        private volatile Set<String> exposedNames = Set.of();

        private SourceEndpoint(SessionSource source) {
            this.source = source;
        }

        /**
         * 建/取 transport。
         *
         * <p>{@code contextExtractor} 是会话身份的唯一来源：claude 侧在 mcp.json 里写了
         * {@value JavaToolMcpServer#SESSION_HEADER} 头，默认的 {@code McpTransportContext.EMPTY}
         * 会把它丢掉，所以必须自己取出来放进 context，供 callHandler 转给工具。
         */
        private synchronized HttpServletStatelessServerTransport transport(McpJsonMapper jsonMapper) {
            if (transport == null) {
                transport = HttpServletStatelessServerTransport.builder()
                        .messageEndpoint(JavaToolMcpServer.messageEndpoint(source))
                        .jsonMapper(jsonMapper)
                        .contextExtractor(request -> {
                            String sessionKey = request.getHeader(JavaToolMcpServer.SESSION_HEADER);
                            return McpTransportContext.create(Map.of(
                                    JavaToolMcpServer.CTX_SESSION_KEY,
                                    sessionKey == null ? "" : sessionKey));
                        })
                        .build();
            }
            return transport;
        }
    }

    /**
     * 一个已暴露的 MCP tool：claude 侧看到的 schema + 回本进程调 Java 方法的入口。
     */
    private static final class ExposedTool {

        private final String name;
        private final McpStatelessServerFeatures.SyncToolSpecification spec;
        private final Object instance;
        private final Method method;
        private final String[] paramNames;
        private final JavaType[] paramTypes;

        private ExposedTool(String name, McpSchema.Tool tool, Object instance, Method method,
                            String[] paramNames, JavaType[] paramTypes) {
            this.name = name;
            this.instance = instance;
            this.method = method;
            this.paramNames = paramNames;
            this.paramTypes = paramTypes;
            this.spec = McpStatelessServerFeatures.SyncToolSpecification.builder()
                    .tool(tool)
                    // transport 的 contextExtractor 把 X-Mango-Session 头取进了 ctx，这里转成
                    // MangoToolContext——工具对象是反射造出来的普通对象，读不到 ctx。
                    // 同步 handler 一次调用占一个线程，finally 里 clear 不会串到别的调用上。
                    .callHandler((McpTransportContext ctx, McpSchema.CallToolRequest request) -> {
                        MangoToolContext.set(ctx == null ? null
                                : (String) ctx.get(JavaToolMcpServer.CTX_SESSION_KEY));
                        try {
                            return invoke(request.arguments());
                        } finally {
                            MangoToolContext.clear();
                        }
                    })
                    .build();
        }

        static ExposedTool of(String toolName, String toolDescription, Object instance, Method method,
                              MangoToolMethod annotation, McpJsonMapper jsonMapper) {
            String name = sanitizeName(annotation.name().isBlank()
                    ? toolName + "_" + method.getName()
                    : annotation.name());
            if (name.isEmpty()) {
                return null;
            }

            String description = annotation.description().isBlank()
                    ? (toolDescription != null && !toolDescription.isBlank()
                        ? toolDescription + " / " + method.getName() : method.getName())
                    : annotation.description();

            Parameter[] parameters = method.getParameters();
            String[] paramNames = new String[parameters.length];
            JavaType[] paramTypes = new JavaType[parameters.length];

            ObjectNode schema = MAPPER.createObjectNode();
            schema.put("type", "object");
            ObjectNode properties = schema.putObject("properties");
            List<String> required = new ArrayList<>();

            for (int i = 0; i < parameters.length; i++) {
                MangoToolParam param = parameters[i].getAnnotation(MangoToolParam.class);
                String paramName = param != null && !param.name().isBlank()
                        ? param.name() : parameters[i].getName();
                paramNames[i] = paramName;
                paramTypes[i] = MAPPER.getTypeFactory().constructType(parameters[i].getParameterizedType());

                ObjectNode property = properties.putObject(paramName);
                property.put("type", jsonType(paramTypes[i]));
                if (param != null && !param.description().isBlank()) {
                    property.put("description", param.description());
                }
                if (param == null || param.required()) {
                    required.add(paramName);
                }
            }
            if (required.isEmpty()) {
                // 一个都不必填就不写 required；空数组虽然合法但没必要
                schema.remove("required");
            } else {
                ArrayNode requiredNode = schema.putArray("required");
                required.forEach(requiredNode::add);
            }

            McpSchema.Tool tool = McpSchema.Tool.builder()
                    .name(name)
                    .description(description)
                    .inputSchema(jsonMapper, schema.toString())
                    .build();
            return new ExposedTool(name, tool, instance, method, paramNames, paramTypes);
        }

        String name() {
            return name;
        }

        McpStatelessServerFeatures.SyncToolSpecification spec() {
            return spec;
        }

        /**
         * 调 Java 方法并把返回值包成 MCP 结果。
         *
         * <p>工具自己抛异常算「工具执行失败」，按 MCP 约定回 {@code isError=true} 的<b>正常结果</b>，
         * 而不是 JSON-RPC 错误——这样模型能看到失败原因并自己决定要不要重试。
         */
        McpSchema.CallToolResult invoke(Map<String, Object> arguments) {
            try {
                Object[] args = new Object[paramNames.length];
                for (int i = 0; i < paramNames.length; i++) {
                    Object raw = arguments == null ? null : arguments.get(paramNames[i]);
                    if (raw == null && paramTypes[i].isPrimitive()) {
                        // 可选的基本类型参数缺省：留给 JVM 默认值，别让它落成 null 炸掉
                        continue;
                    }
                    args[i] = MAPPER.convertValue(raw, paramTypes[i]);
                }
                Object result = method.invoke(instance, args);
                return McpSchema.CallToolResult.builder()
                        .addTextContent(result == null ? "" : String.valueOf(result))
                        .build();
            } catch (InvocationTargetException e) {
                return errorResult(e.getCause() != null ? e.getCause() : e);
            } catch (Exception e) {
                return errorResult(e);
            }
        }

        private static McpSchema.CallToolResult errorResult(Throwable cause) {
            log.warn("MCP 工具调用失败", cause);
            return McpSchema.CallToolResult.builder()
                    .addTextContent("调用失败：" + cause.getClass().getSimpleName()
                            + (cause.getMessage() != null ? " - " + cause.getMessage() : ""))
                    .isError(true)
                    .build();
        }

        /** MCP 工具名只认 {@code [A-Za-z0-9_-]}，且不超过 64 字符。 */
        private static String sanitizeName(String raw) {
            String name = raw.replaceAll("[^A-Za-z0-9_-]", "_");
            return name.length() > 64 ? name.substring(0, 64) : name;
        }

        private static String jsonType(JavaType type) {
            Class<?> raw = type.getRawClass();
            if (raw == boolean.class || raw == Boolean.class) {
                return "boolean";
            }
            if (raw == int.class || raw == Integer.class || raw == long.class || raw == Long.class
                    || raw == short.class || raw == Short.class || raw == byte.class || raw == Byte.class) {
                return "integer";
            }
            if (raw == double.class || raw == Double.class || raw == float.class || raw == Float.class) {
                return "number";
            }
            if (type.isCollectionLikeType() || type.isArrayType()) {
                return "array";
            }
            if (type.isMapLikeType()) {
                return "object";
            }
            return "string";
        }
    }
}
