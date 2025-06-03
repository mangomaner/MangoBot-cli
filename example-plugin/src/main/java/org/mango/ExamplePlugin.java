package org.mango;

import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.community.model.dashscope.QwenChatRequestParameters;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.mango.mangobot.annotation.PluginPriority;
import org.mango.mangobot.annotation.QQ.method.TextMessage;
import org.mango.mangobot.annotation.QQ.parameter.Content;
import org.mango.mangobot.annotation.QQ.parameter.SenderId;
import org.mango.mangobot.plugin.Plugin;
import org.mango.mangobot.plugin.PluginContext;
import redis.clients.jedis.Jedis;

public class ExamplePlugin implements Plugin {

    private QwenChatModel qwenChatModel;

    // 必须要有这个构造函数
    public ExamplePlugin() { }
    @Override
    public void onEnable(PluginContext context) {
        qwenChatModel = (QwenChatModel)context.getBean("qwenChatModel");


        Jedis jedis = new Jedis("http://localhost:6379");
        System.out.println("服务器连接成功");
        //直接用set方法存入  get方法读取
        //设置 redis 字符串数据
        jedis.set("botTest", "Hello World!");
        // 获取存储的数据并输出
        System.out.println("redis 存储的字符串为: "+ jedis.get("botTest"));


        System.out.println("ExamplePlugin 已启用");
    }

    @Override
    public void onDisable() {
        System.out.println("ExamplePlugin 已禁用");
    }

    @TextMessage
    @PluginPriority(1)
    public void handleTextMessage(@SenderId String fromUser, @Content String content) {
        System.out.println("[ExamplePlugin] 收到文本消息：" + content + " from: " + fromUser);
    }

    private String chatWithModel(String question) {
        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from(question))
                .parameters(QwenChatRequestParameters.builder()
                        .temperature(0.5)
                        .modelName("qwen-turbo") // 设置模型名称
                        .enableSearch(false)    // 是否联网搜索
                        .build())
                .build();
        ChatResponse chatResponse = qwenChatModel.chat(request);
        return chatResponse.aiMessage().text();
    }
}
