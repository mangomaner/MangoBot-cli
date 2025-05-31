# MangoBot-cli
MangoBot开发框架（不含其他功能）

### 配置
1. 如果您没有启动spring boot项目的经验，可参考`https://blog.csdn.net/weixin_45393094/article/details/123674367`的步骤一、二（无需配置数据库）。
2. 在代码路径`src/main/resources/application-test.yml`，配置bot账号、白名单群聊、阿里通义api-key（可选）
3. 浏览器输入`http://localhost:8765/doc.html#/home`测试接口

### 连接oneBot
请在 `反向websocket服务` 处填上：```ws://localhost:8765```

### 开发
`src/main/java/org/mango/mangobot/messageHandler`，内部有详细注释
