package io.github.mangomaner.mangobot.module.agent.capability.tool;

import io.github.mangomaner.mangobot.module.agent.model.enums.SessionSource;
import jakarta.servlet.http.HttpServlet;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 把 {@link JavaToolMcpServer} 的 servlet 挂到应用自己的 servlet 容器上。
 *
 * <p>claude 是同一台机器上的子进程，直接连 {@code http://127.0.0.1:<server.port>/mcp/<来源>} 即可，
 * 不需要另起端口或另开 HTTP 服务。
 *
 * <p>一个来源一个 servlet，所以一个来源一个 {@code @Bean} 方法——<b>不能</b>让一个方法返回
 * {@code List<ServletRegistrationBean>}：那样 Spring 只注册那个 List 本身，三个 servlet 一个都不会
 * 生效，表现为 {@code /mcp/xxx} 全部 404。
 */
@Configuration
public class McpServletConfig {

    @Bean
    public ServletRegistrationBean<HttpServlet> mangobotMcpWebServlet(JavaToolMcpServer mcpServer) {
        return registration(mcpServer, SessionSource.WEB);
    }

    @Bean
    public ServletRegistrationBean<HttpServlet> mangobotMcpGroupServlet(JavaToolMcpServer mcpServer) {
        return registration(mcpServer, SessionSource.GROUP);
    }

    @Bean
    public ServletRegistrationBean<HttpServlet> mangobotMcpPrivateServlet(JavaToolMcpServer mcpServer) {
        return registration(mcpServer, SessionSource.PRIVATE);
    }

    private static ServletRegistrationBean<HttpServlet> registration(JavaToolMcpServer mcpServer,
                                                                    SessionSource source) {
        ServletRegistrationBean<HttpServlet> registration = new ServletRegistrationBean<>(
                mcpServer.transport(source), JavaToolMcpServer.messageEndpoint(source) + "/*");
        registration.setName("mangobotMcpServlet-" + source.getSourceKey());
        return registration;
    }
}
