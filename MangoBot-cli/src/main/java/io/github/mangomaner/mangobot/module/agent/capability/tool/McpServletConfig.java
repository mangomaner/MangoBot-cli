package io.github.mangomaner.mangobot.module.agent.capability.tool;

import jakarta.servlet.http.HttpServlet;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 把 {@link JavaToolMcpServer} 的 servlet 挂到应用自己的 servlet 容器上。
 *
 * <p>claude 是同一台机器上的子进程，直接连 {@code http://127.0.0.1:<server.port>/mcp} 即可，
 * 不需要另起端口或另开 HTTP 服务。
 */
@Configuration
@RequiredArgsConstructor
public class McpServletConfig {

    @Bean
    public ServletRegistrationBean<HttpServlet> mangobotMcpServlet(JavaToolMcpServer mcpServer) {
        return new ServletRegistrationBean<HttpServlet>(mcpServer.transport(),
                JavaToolMcpServer.MESSAGE_ENDPOINT + "/*");
    }
}
