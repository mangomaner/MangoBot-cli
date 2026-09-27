package io.github.mangomaner.mangobot.infra.claude.spec;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 一条要写进 claude 那份 {@code mcp.json} 的 MCP 连接。
 *
 * <p>{@code config} 是该 server 的字段字面量，由调用方按 MCP 的线上格式备好；infra 只负责落盘与推导放行规则。
 *
 * @param name   mcp.json 里的键，也是放行规则 {@code mcp__<name>__*} 的名字
 * @param config server 对象字面量
 */
public record McpServerSpec(String name, ObjectNode config) {

    public McpServerSpec {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("MCP server 名不能为空");
        }
    }

    // claude 认的放行规则；按 server 粒度，因为外部连接有哪些工具只有连上才知道
    public String allowRule() {
        return "mcp__" + name + "__*";
    }
}
