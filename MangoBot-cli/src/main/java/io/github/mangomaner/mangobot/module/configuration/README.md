# MangoBot 配置系统说明文档

## 概述

MangoBot 配置系统采用模块化设计，将配置按类型分离存储，提供更清晰的配置管理方式和更好的可维护性。

## 架构设计

### 模块结构

```
configuration/
├── annotation/                    # 配置相关注解
│   └── InjectConfig.java          # 插件配置注入注解
├── controller/                    # 配置管理 API
│   ├── ModelConfigController.java # 模型配置控制器
│   ├── ModelRoleController.java   # 模型角色控制器
│   ├── SystemConfigController.java# 系统配置控制器
│   ├── BotConfigController.java   # Bot 配置控制器
│   └── PluginConfigController.java# 插件配置控制器
├── event/                         # 配置事件
│   ├── ConfigurationEvent.java    # 配置事件基类
│   ├── ModelRoleChangedEvent.java # 模型角色变更事件
│   ├── ModelConfigChangedEvent.java
│   ├── SystemConfigChangedEvent.java
│   ├── BotConfigChangedEvent.java
│   └── PluginConfigChangedEvent.java
├── mapper/                        # 数据访问层
│   ├── ModelProviderMapper.java
│   ├── ModelConfigMapper.java
│   ├── ModelRoleMapper.java
│   ├── SystemConfigMapper.java
│   ├── BotConfigMapper.java
│   └── PluginConfigMapper.java
├── model/                         # 数据模型
│   ├── domain/                    # 实体类
│   │   ├── ModelProvider.java
│   │   ├── ModelConfig.java
│   │   ├── ModelRole.java
│   │   ├── SystemConfig.java
│   │   ├── BotConfig.java
│   │   └── PluginConfigEntity.java
│   ├── dto/                       # 数据传输对象
│   │   ├── model/
│   │   ├── system/
│   │   ├── bot/
│   │   └── plugin/
│   └── vo/                        # 视图对象
│       ├── ModelProviderVO.java
│       ├── ModelConfigVO.java
│       ├── ModelRoleVO.java
│       ├── SystemConfigVO.java
│       ├── BotConfigVO.java
│       ├── PluginConfigVO.java
│       └── ModelTestResultVO.java
└── service/                       # 服务层
    ├── ModelProvider.java         # 模型提供者接口（对外提供模型实例）
    ├── ModelProviderService.java  # 供应商服务
    ├── ModelConfigService.java    # 模型配置服务
    ├── ModelRoleService.java      # 模型角色服务
    ├── SystemConfigService.java   # 系统配置服务
    ├── BotConfigService.java      # Bot 配置服务
    ├── PluginConfigService.java
    └── impl/
```

## 数据库表结构

### 1. model_providers（模型供应商表）

存储 API 供应商的公共配置。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | INTEGER | 主键 |
| name | TEXT | 供应商名称（openai, anthropic, deepseek 等） |
| base_url | TEXT | API 基础地址 |
| api_key | TEXT | API 密钥 |
| timeout | INTEGER | 默认超时时间（秒） |
| description | TEXT | 描述 |
| is_enabled | INTEGER | 是否启用 |

### 2. model_configs（模型配置表）

存储具体模型的配置信息。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | INTEGER | 主键 |
| model_name | TEXT | 模型标识（gpt-4o, deepseek-chat 等） |
| provider_id | INTEGER | 关联供应商 |
| temperature | REAL | 温度参数 |
| max_tokens | INTEGER | 最大 Token 数 |
| top_p | REAL | Top-P 参数 |
| timeout | INTEGER | 超时时间 |
| description | TEXT | 描述 |
| is_enabled | INTEGER | 是否启用 |

### 3. model_roles（模型角色表）

定义模型角色与模型配置的映射关系。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | INTEGER | 主键 |
| role_key | TEXT | 角色标识（main, assistant, image, embedding） |
| role_name | TEXT | 角色名称（主模型、助手模型等） |
| model_config_id | INTEGER | 关联的模型配置ID |
| description | TEXT | 描述 |

### 4. system_configs（系统配置表）

存储系统级全局配置（系统名称、日志级别等），**无 bot_id**。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | INTEGER | 主键 |
| config_key | TEXT | 配置键（唯一） |
| config_value | TEXT | 配置值（支持 JSON） |
| config_type | TEXT | 类型（见 ConfigType 枚举） |
| metadata | TEXT | 前端元数据（JSON 格式） |
| description | TEXT | 描述 |
| explain | TEXT | 详细说明 |
| category | TEXT | 分类 |
| editable | INTEGER | 是否可编辑 |

**系统配置示例：**
- `system.name` - 系统名称
- `system.log_level` - 日志级别
- `system.data_retention_days` - 数据保留天数
- `system.enable_web` - 是否启用 Web 界面
- `system.max_connections` - 最大连接数

### 5. bot_configs（Bot 配置表）

存储 Bot 级别配置（白名单、黑名单等），支持多 Bot 配置。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | INTEGER | 主键 |
| bot_id | INTEGER | Bot ID（null 表示默认配置） |
| config_key | TEXT | 配置键 |
| config_value | TEXT | 配置值（支持 JSON） |
| config_type | TEXT | 类型（见 ConfigType 枚举） |
| metadata | TEXT | 前端元数据（JSON 格式） |
| description | TEXT | 描述 |
| explain | TEXT | 详细说明 |
| category | TEXT | 分类 |
| editable | INTEGER | 是否可编辑 |

**多 Bot 配置说明：**
- `bot_id = null` 表示默认配置，用于无 Bot 连接时显示
- 当有新的 Bot 连接时，不会立即复制配置，而是采用"懒加载"模式
- 在修改配置时，若 Bot 专属配置不存在则自动创建，已存在则更新
- 查询时优先返回 Bot 专属配置，其次返回默认配置

**Bot 配置示例：**
- `group.whitelist` - 群组白名单
- `group.blacklist` - 群组黑名单
- `group.enable_list` - 启用群组黑白名单
- `private.whitelist` - 私聊白名单
- `private.blacklist` - 私聊黑名单
- `private.enable_list` - 启用私聊黑白名单

### 6. plugin_configs（插件配置表）

存储插件自定义配置，支持多 Bot 配置。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | INTEGER | 主键 |
| plugin_id | INTEGER | 关联插件 |
| bot_id | INTEGER | Bot ID（null 表示默认配置） |
| config_key | TEXT | 配置键（相对于插件） |
| config_value | TEXT | 配置值 |
| config_type | TEXT | 类型（见 ConfigType 枚举） |
| metadata | TEXT | 前端元数据（JSON 格式） |
| description | TEXT | 描述 |
| explain | TEXT | 详细说明 |
| editable | INTEGER | 是否可编辑 |

**多 Bot 配置说明：**
- 与 bot_configs 相同的懒加载机制
- 每个 Bot 可以有独立的插件配置

## 配置类型（ConfigType）

支持丰富的配置类型，满足前端多样化展示需求：

| 类型 | 前端组件 | 数据库存储格式 | 说明 |
|------|----------|----------------|------|
| STRING | 文本输入框 | 原始字符串 | 基础字符串 |
| INTEGER | 数字输入框 | 数字字符串 | 整数 |
| LONG | 数字输入框 | 数字字符串 | 长整数 |
| DOUBLE | 数字输入框 | 数字字符串 | 浮点数 |
| BOOLEAN | 开关 | `"true"`/`"false"` 或 `"1"`/`"0"` | 布尔值 |
| SELECT | 下拉选择 | 选中的 value | 单选下拉 |
| MULTI_SELECT | 多选下拉 | JSON 数组 `["a","b"]` | 多选下拉 |
| DATE | 日期选择 | ISO 格式 `"2024-01-15"` | 日期 |
| DATETIME | 日期时间选择 | ISO 格式 `"2024-01-15T10:30:00"` | 日期时间 |
| TIMESTAMP | 时间戳显示 | 毫秒时间戳 | 时间戳 |
| LIST | 列表编辑器 | JSON 数组 `["item1","item2"]` | 字符串列表 |
| KEY_VALUE | 键值对编辑器 | JSON 对象 `{"key1":"value1"}` | 键值对 |
| KEY_VALUE_LIST | 键值对列表 | JSON 数组 `[{"key":"k1","value":"v1"}]` | 键值对列表 |
| RANGE_INTEGER | 整数范围滑块 | JSON `{"min":0,"max":100}` | 整数范围 |
| RANGE_DOUBLE | 浮点范围滑块 | JSON `{"min":0.0,"max":1.0}` | 浮点范围 |
| JSON | JSON 编辑器 | 原始 JSON 字符串 | 自定义 JSON |
| COLOR | 颜色选择器 | 十六进制 `"#FF5733"` | 颜色 |
| URL | URL 输入框 | URL 字符串 | 网址链接 |
| PASSWORD | 密码输入框 | 加密/明文字符串 | 密码 |

### metadata 字段说明

metadata 用于存储前端渲染所需的额外信息：

```json
{
  "options": [
    {"label": "选项1", "value": "1"},
    {"label": "选项2", "value": "2"}
  ],
  "min": 0,
  "max": 100,
  "step": 1,
  "placeholder": "请输入",
  "format": "yyyy-MM-dd",
  "clearable": true,
  "filterable": true
}
```

**metadata 字段用途：**
- `SELECT/MULTI_SELECT`: `options` 选项列表
- `RANGE_INTEGER/RANGE_DOUBLE`: `min`, `max`, `step` 范围参数
- `LIST`: `itemType` 列表项类型
- `KEY_VALUE`: `keyPlaceholder`, `valuePlaceholder`
- `DATE/DATETIME`: `format`, `minDate`, `maxDate`

## API 接口

### 模型角色 API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/configuration/model/roles | 获取所有角色 |
| GET | /api/configuration/model/roles/{roleKey} | 获取角色详情 |
| PUT | /api/configuration/model/roles/{roleKey} | 更新角色对应的模型 |

### 模型配置 API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/configuration/model/providers | 获取所有供应商 |
| GET | /api/configuration/model/providers/{id} | 获取供应商详情 |
| POST | /api/configuration/model/providers | 创建供应商 |
| PUT | /api/configuration/model/providers | 更新供应商 |
| DELETE | /api/configuration/model/providers/{id} | 删除供应商 |
| POST | /api/configuration/model/providers/{id}/test | 测试供应商连接 |
| GET | /api/configuration/model/configs | 获取所有模型配置 |
| GET | /api/configuration/model/configs/{id} | 获取模型配置详情 |
| POST | /api/configuration/model/configs | 创建模型配置 |
| PUT | /api/configuration/model/configs | 更新模型配置 |
| DELETE | /api/configuration/model/configs/{id} | 删除模型配置 |
| POST | /api/configuration/model/configs/{id}/test | 测试模型 |

### 系统配置 API（全局配置，无 botId）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/configuration/system | 获取所有系统配置 |
| GET | /api/configuration/system/category/{category} | 按分类获取配置 |
| GET | /api/configuration/system/{configKey} | 获取指定配置 |
| POST | /api/configuration/system | 创建系统配置 |
| PUT | /api/configuration/system | 更新系统配置 |
| PUT | /api/configuration/system/{configKey} | 更新配置值 |
| DELETE | /api/configuration/system/{id} | 删除系统配置 |

### Bot 配置 API（Bot 级别配置，有 botId）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/configuration/bot | 获取所有默认 Bot 配置（bot_id 为 null） |
| GET | /api/configuration/bot/bot/{botId} | 根据 Bot ID 获取配置（懒加载模式） |
| GET | /api/configuration/bot/category/{category} | 按分类获取默认配置 |
| GET | /api/configuration/bot/{configKey} | 获取指定默认配置 |
| GET | /api/configuration/bot/{configKey}/bot/{botId} | 根据 Key 和 Bot ID 获取配置 |
| POST | /api/configuration/bot | 创建 Bot 配置 |
| PUT | /api/configuration/bot | 更新 Bot 配置 |
| PUT | /api/configuration/bot/{configKey} | 更新默认配置值 |
| PUT | /api/configuration/bot/{configKey}/bot/{botId} | 更新 Bot 专属配置值（懒加载） |
| DELETE | /api/configuration/bot/{id} | 删除 Bot 配置 |

### 插件配置 API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/configuration/plugin | 获取所有默认插件配置（bot_id 为 null） |
| GET | /api/configuration/plugin/bot/{botId} | 根据 Bot ID 获取配置（懒加载模式） |
| GET | /api/configuration/plugin/{pluginId} | 获取插件的默认配置列表 |
| GET | /api/configuration/plugin/{pluginId}/bot/{botId} | 获取插件的 Bot 专属配置列表 |
| GET | /api/configuration/plugin/{pluginId}/{configKey} | 获取单个默认插件配置 |
| GET | /api/configuration/plugin/{pluginId}/{configKey}/bot/{botId} | 获取单个 Bot 专属插件配置 |
| PUT | /api/configuration/plugin | 更新插件配置 |
| PUT | /api/configuration/plugin/{pluginId}/{configKey} | 更新默认插件配置值 |
| PUT | /api/configuration/plugin/{pluginId}/{configKey}/bot/{botId} | 更新 Bot 专属插件配置值（懒加载） |

## 事件系统

配置变更时通过 `MangoEventPublisher` 广播事件：

### 事件类型

| 事件类 | 说明 |
|--------|------|
| ModelRoleChangedEvent | 模型角色变更事件（角色与模型映射变更） |
| ModelConfigChangedEvent | 模型配置变更事件 |
| SystemConfigChangedEvent | 系统配置变更事件（全局配置） |
| BotConfigChangedEvent | Bot 配置变更事件（Bot 级别配置） |
| PluginConfigChangedEvent | 插件配置变更事件 |

### 监听配置变更

```java
@MangoBotEventListener
public boolean onModelRoleChanged(ModelRoleChangedEvent event) {
    String roleKey = event.getRoleKey();
    Long oldConfigId = event.getOldModelConfigId();
    Long newConfigId = event.getNewModelConfigId();
    // 处理角色变更
    return true;
}
```

## AI 模型管理

### 模型角色

| 角色标识 | 角色名称 | 说明 |
|---------|---------|------|
| main | 主模型 | 用于主要对话任务 |
| assistant | 助手模型 | 用于简单任务，节省成本 |
| image | 图片模型 | 用于图片理解任务 |
| embedding | 向量模型 | 用于文本向量化 |

### 获取模型配置

通过 `MangoModelApi` 静态工具类解析模型角色的完整配置元信息（凭据 + 生成参数）：

```java
import io.github.mangomaner.mangobot.api.MangoModelApi;
import io.github.mangomaner.mangobot.api.enums.ModelRole;
import io.github.mangomaner.mangobot.module.configuration.model.domain.ModelEndpoint;

public void example() {
    // 解析主模型的端点凭据与生成参数
    ModelEndpoint main = MangoModelApi.resolveEndpoint(ModelRole.MAIN);

    // 解析图片模型（用于直接发 HTTP 请求的场景）
    ModelEndpoint image = MangoModelApi.resolveEndpoint(ModelRole.IMAGE);

    // 获取模型配置信息
    ModelConfigVO config = MangoModelApi.getModelConfig("main");

    // 获取所有角色
    List<ModelRoleVO> roles = MangoModelApi.getAllRoles();
}
```

### MangoModelApi 方法列表

| 方法 | 说明 |
|------|------|
| `resolveEndpoint(role)` | 解析指定角色的完整配置元信息（地址 / 密钥 / 模型名 / 供应商名 / 生成参数） |
| `getModelConfig(roleKey)` | 获取指定角色的模型配置详情 |
| `getAllRoles()` | 获取所有角色配置 |
| `getRole(roleKey)` | 获取指定角色的配置 |

### 模型角色变更

当角色对应的模型变更时，`ModelProviderImpl` 会自动监听 `ModelRoleChangedEvent` 并重新加载模型实例。

## 配置 API（MangoConfigApi）

### 配置类型说明

| 配置类型 | 说明 | 特点 |
|---------|------|------|
| 系统配置 | 全局配置，如系统名称、日志级别 | 无 botId，全局唯一 |
| Bot 配置 | Bot 级别配置，如黑白名单 | 有 botId，支持多 Bot |
| 插件配置 | 插件自定义配置 | 有 pluginId 和 botId |

### 使用示例

```java
import io.github.mangomaner.mangobot.api.MangoConfigApi;

// ==================== 系统配置（全局配置） ====================

// 获取系统配置值
String systemName = MangoConfigApi.getSystemConfigValue("system.name", "MangoBot");
String logLevel = MangoConfigApi.getSystemConfigValue("system.log_level", "INFO");

// 更新系统配置值（仅主程序可调用）
MangoConfigApi.updateSystemConfigValue("system.log_level", "DEBUG");

// ==================== Bot 配置（Bot 级别配置） ====================

// 获取 Bot 配置值（优先 Bot 专属配置，其次默认配置）
String whitelist = MangoConfigApi.getBotConfigValue("group.whitelist", botId, "[]");
String enableList = MangoConfigApi.getBotConfigValue("group.enable_list", botId, "true");

// 更新 Bot 配置值（仅主程序可调用，懒加载模式）
MangoConfigApi.updateBotConfigValue("group.whitelist", botId, "[\"123456789\"]");

// ==================== 插件配置（仅插件可调用） ====================

// 获取当前插件的配置值
String timeout = MangoConfigApi.getPluginConfigValue("timeout", botId, "30");

// 更新当前插件的配置值
MangoConfigApi.updatePluginConfigValue("timeout", botId, "60");
```

## 插件配置注入

### 使用 @InjectConfig 注解

在插件类中标记需要注入的配置字段：

```java
@PluginDescribe(name = "MyPlugin", author = "developer", version = "1.0.0")
public class MyPlugin implements Plugin {

    @InjectConfig(key = "timeout", defaultValue = "30", description = "超时时间（秒）")
    private Integer timeout;

    @InjectConfig(key = "greeting", defaultValue = "你好", description = "问候语")
    private String greeting;

    @InjectConfig(key = "enabled", defaultValue = "true", description = "是否启用")
    private Boolean enabled;

    @Override
    public void onEnable() {
        // 配置字段已自动注入
        System.out.println("超时时间: " + timeout);
        System.out.println("问候语: " + greeting);
    }
}
```

### 支持的类型

- STRING
- INTEGER
- LONG
- DOUBLE
- BOOLEAN

### 兼容旧注解

仍然支持 `@PluginConfig` 和 `@PluginConfigs` 注解：

```java
@PluginConfig(key = "plugin.example.hello", value = "你好", description = "测试配置")
@PluginDescribe(name = "ExamplePlugin", author = "mangomaner", version = "1.0.0")
public class ExamplePlugin implements Plugin {
    // ...
}
```

## 迁移指南

### 从旧配置系统迁移

1. **删除旧数据库**：删除 `data/mangobot.db` 文件
2. **重启应用**：应用启动时会自动创建新的表结构
3. **重新配置**：通过 API 或前端界面重新配置模型和系统设置

### 配置迁移对照

| 旧配置键 | 新配置位置 |
|---------|-----------|
| main.model.main_model | model_roles 表（role_key='main'） |
| main.model.assistant_model | model_roles 表（role_key='assistant'） |
| main.model.image_model | model_roles 表（role_key='image'） |
| main.model.embedding_model | model_roles 表（role_key='embedding'） |
| main.QQ.group.whitelist | bot_configs 表（config_key='group.whitelist'） |
| main.QQ.group.blacklist | bot_configs 表（config_key='group.blacklist'） |
| plugin.* | plugin_configs 表 |

## 最佳实践

1. **模型配置**：先创建供应商，再创建模型配置，最后通过角色分配模型
2. **角色管理**：使用角色来灵活切换不同用途的模型，而非硬编码别名
3. **配置命名**：使用点分隔符命名配置键，如 `feature.timeout`
4. **类型选择**：根据配置值选择合适的类型（STRING, INTEGER, BOOLEAN, JSON）
5. **事件监听**：需要响应配置变更时，监听对应的事件类型
6. **插件配置**：使用 `@InjectConfig` 注解实现类型安全的配置注入
7. **配置分类**：系统配置用于全局设置，Bot 配置用于 Bot 级别设置

## 多 Bot 配置机制

### 设计理念

配置系统支持多 Bot 场景，采用"懒加载"模式，避免在 Bot 连接时立即复制大量配置数据。

### 工作流程

1. **初始化阶段**
   - 数据库中存在 `bot_id = null` 的默认配置
   - 前端在无 Bot 连接时显示默认配置

2. **Bot 连接阶段**
   - 新 Bot 连接时，不立即复制配置
   - 前端请求配置时，返回默认配置和 Bot 专属配置（优先展示 Bot 专属配置）

3. **配置修改阶段**
   - 用户修改配置时，检查是否存在 Bot 专属配置
   - 若存在：直接更新
   - 若不存在：创建新的 Bot 专属配置（复制默认配置的结构，使用新的值）

### 前端集成建议

```typescript
// 获取 Bot 配置时传入当前 Bot ID
const botId = botStore.currentBotId;
const configs = await getBotConfigsByBotId(botId);

// 更新 Bot 配置时使用懒加载接口
await updateBotConfigValueByBotId(configKey, botId, newValue);
```

### 配置优先级

查询 Bot 配置时的优先级：
1. Bot 专属配置（`bot_id = {具体Bot ID}`）
2. 默认配置（`bot_id = null`）

### 数据隔离

- 每个 Bot 的专属配置完全独立
- 修改默认配置不会影响已有 Bot 专属配置
- 删除 Bot 时可选择是否删除其专属配置
