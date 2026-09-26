-- MangoBot 数据库初始化脚本
-- 创建时间: 2026-01-17
-- 最后更新: 2026-03-12（配置系统重构 - 角色与模型分离）

-- ============================================
-- 模型配置相关表
-- ============================================

-- 模型供应商表：存储 API 供应商的公共配置
CREATE TABLE IF NOT EXISTS model_providers (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    name            TEXT NOT NULL UNIQUE,            -- 供应商名称（openai, anthropic, deepseek 等）
    base_url        TEXT NOT NULL,                   -- API 基础地址
    api_key         TEXT NOT NULL,                   -- API 密钥
    timeout         INTEGER DEFAULT 30,              -- 默认超时时间（秒）
    description     TEXT,                            -- 描述
    is_enabled      INTEGER DEFAULT 1,               -- 是否启用：0-禁用, 1-启用
    created_at      INTEGER DEFAULT (strftime('%s', 'now') * 1000),
    updated_at      INTEGER DEFAULT (strftime('%s', 'now') * 1000)
);

-- 模型配置表：存储具体模型配置
CREATE TABLE IF NOT EXISTS model_configs (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    model_name      TEXT NOT NULL,                   -- 模型标识（gpt-4o, deepseek-chat 等）
    provider_id     INTEGER NOT NULL,                -- 关联供应商
    temperature     REAL DEFAULT 0.7,                -- 温度参数
    max_tokens      INTEGER,                         -- 最大 Token 数
    top_p           REAL,                            -- Top-P 参数
    timeout         INTEGER,                         -- 超时时间（覆盖供应商默认值）
    description     TEXT,                            -- 描述
    is_enabled      INTEGER DEFAULT 1,               -- 是否启用：0-禁用, 1-启用
    created_at      INTEGER DEFAULT (strftime('%s', 'now') * 1000),
    updated_at      INTEGER DEFAULT (strftime('%s', 'now') * 1000),
    FOREIGN KEY (provider_id) REFERENCES model_providers(id)
);

-- 模型角色表：定义模型角色与模型配置的映射关系
CREATE TABLE IF NOT EXISTS model_roles (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    role_key        TEXT NOT NULL UNIQUE,            -- 角色标识：main, assistant, image, embedding
    role_name       TEXT NOT NULL,                   -- 角色名称：主模型、助手模型等
    model_config_id INTEGER,                         -- 关联的模型配置ID（可为空表示未配置）
    description     TEXT,                            -- 描述
    created_at      INTEGER DEFAULT (strftime('%s', 'now') * 1000),
    updated_at      INTEGER DEFAULT (strftime('%s', 'now') * 1000),
    FOREIGN KEY (model_config_id) REFERENCES model_configs(id)
);

-- 模型供应商索引
CREATE INDEX IF NOT EXISTS idx_model_providers_name ON model_providers (name);
CREATE INDEX IF NOT EXISTS idx_model_providers_enabled ON model_providers (is_enabled);

-- 模型配置索引
CREATE INDEX IF NOT EXISTS idx_model_configs_provider ON model_configs (provider_id);

-- 模型角色索引
CREATE INDEX IF NOT EXISTS idx_model_roles_key ON model_roles (role_key);
CREATE INDEX IF NOT EXISTS idx_model_roles_config ON model_roles (model_config_id);

-- ============================================
-- Bot 配置表
-- ============================================

-- Bot 配置表：存储 Bot 级别配置（白名单、黑名单等）
CREATE TABLE IF NOT EXISTS bot_configs (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    bot_id          TEXT,                           -- Bot ID（null 表示默认配置，使用 TEXT 兼容多平台）
    config_key      TEXT NOT NULL,                   -- 配置键
    config_value    TEXT,                            -- 配置值（支持 JSON）
    config_type     TEXT NOT NULL DEFAULT 'STRING',  -- 类型：见 ConfigType 枚举
    metadata        TEXT,                            -- 前端元数据（JSON格式：选项列表、范围限制等）
    description     TEXT,                            -- 描述
    explain         TEXT,                            -- 详细说明
    category        TEXT DEFAULT 'general',          -- 分类
    editable        INTEGER DEFAULT 1,               -- 是否可编辑：0-不可编辑, 1-可编辑
    created_at      INTEGER DEFAULT (strftime('%s', 'now') * 1000),
    updated_at      INTEGER DEFAULT (strftime('%s', 'now') * 1000),
    UNIQUE(bot_id, config_key)
);

-- Bot 配置索引
CREATE INDEX IF NOT EXISTS idx_bot_configs_key ON bot_configs (config_key);
CREATE INDEX IF NOT EXISTS idx_bot_configs_category ON bot_configs (category);
CREATE INDEX IF NOT EXISTS idx_bot_configs_bot_id ON bot_configs (bot_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_bot_configs_unique ON bot_configs (bot_id, config_key);

-- ============================================
-- 系统配置表
-- ============================================

-- 系统配置表：存储系统级全局配置（系统名称、日志级别等）
CREATE TABLE IF NOT EXISTS system_configs (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    config_key      TEXT NOT NULL UNIQUE,            -- 配置键（唯一）
    config_value    TEXT,                            -- 配置值（支持 JSON）
    config_type     TEXT NOT NULL DEFAULT 'STRING',  -- 类型：见 ConfigType 枚举
    metadata        TEXT,                            -- 前端元数据（JSON格式：选项列表、范围限制等）
    description     TEXT,                            -- 描述
    explain         TEXT,                            -- 详细说明
    category        TEXT DEFAULT 'general',          -- 分类
    editable        INTEGER DEFAULT 1,               -- 是否可编辑：0-不可编辑, 1-可编辑
    created_at      INTEGER DEFAULT (strftime('%s', 'now') * 1000),
    updated_at      INTEGER DEFAULT (strftime('%s', 'now') * 1000)
);

-- 系统配置索引
CREATE INDEX IF NOT EXISTS idx_system_configs_key ON system_configs (config_key);
CREATE INDEX IF NOT EXISTS idx_system_configs_category ON system_configs (category);

-- ============================================
-- 插件配置表
-- ============================================

-- 插件配置表：存储插件自定义配置
CREATE TABLE IF NOT EXISTS plugin_configs (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    plugin_id       INTEGER NOT NULL,                -- 关联插件
    bot_id          TEXT,                            -- Bot ID（null 表示默认配置，使用 TEXT 兼容多平台）
    config_key      TEXT NOT NULL,                   -- 配置键（相对于插件）
    config_value    TEXT,                            -- 配置值
    config_type     TEXT NOT NULL DEFAULT 'STRING',  -- 类型：见 ConfigType 枚举
    metadata        TEXT,                            -- 前端元数据（JSON格式：选项列表、范围限制等）
    description     TEXT,                            -- 描述
    explain         TEXT,                            -- 详细说明
    editable        INTEGER DEFAULT 1,               -- 是否可编辑：0-不可编辑, 1-可编辑
    created_at      INTEGER DEFAULT (strftime('%s', 'now') * 1000),
    updated_at      INTEGER DEFAULT (strftime('%s', 'now') * 1000),
    UNIQUE(plugin_id, bot_id, config_key),
    FOREIGN KEY (plugin_id) REFERENCES plugins(id) ON DELETE CASCADE
);

-- 插件配置索引
CREATE INDEX IF NOT EXISTS idx_plugin_configs_plugin ON plugin_configs (plugin_id);
CREATE INDEX IF NOT EXISTS idx_plugin_configs_bot_id ON plugin_configs (bot_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_plugin_configs_unique ON plugin_configs (plugin_id, bot_id, config_key);

-- ============================================
-- 初始数据
-- ============================================

-- 模型供应商初始数据
INSERT INTO model_providers (name, base_url, api_key, description) VALUES
    ('openai', 'https://api.openai.com/v1', '', 'OpenAI'),
    ('siliconflow', 'https://api.siliconflow.cn/v1', '', '硅基流动(siliconflow)'),
    ('dashscope', 'https://dashscope.aliyuncs.com/compatible-mode/v1', '', '阿里云-百炼'),
    ('custom', 'https://api.example.com/v1', '', '自定义接口');

-- 模型配置初始数据
INSERT INTO model_configs (model_name, provider_id, temperature, description) VALUES
    ('不选择', 0, 0.7, 'empty');

-- 模型角色初始数据
INSERT INTO model_roles (role_key, role_name, model_config_id, description) VALUES
    ('main', '主模型', 1, '用于主要对话任务'),
    ('assistant', '助手模型', 1, '用于简单任务，节省成本'),
    ('image', '图片模型', 1, '用于图片理解任务'),
    ('embedding', '向量模型', 1, '用于文本向量化');

-- Bot 配置初始数据（bot_id 为 null 表示默认配置，用于无 Bot 连接时显示）
INSERT INTO bot_configs (bot_id, config_key, config_value, config_type, metadata, description, explain, category, editable) VALUES
    (NULL, 'group.whitelist', '[]', 'GROUP_LIST_SELECTOR', '{"listType":"group"}', '群组白名单', '群号列表', 'BW_list', 1),
    (NULL, 'group.blacklist', '[]', 'GROUP_LIST_SELECTOR', '{"listType":"group"}', '群组黑名单', '群号列表', 'BW_list', 1),
    (NULL, 'group.enable_list', 'true', 'BOOLEAN', NULL, '启用群组黑白名单', '', 'BW_list', 1),
    (NULL, 'private.whitelist', '[]', 'PRIVATE_LIST_SELECTOR', '{"listType":"private"}', '私聊白名单', '用户QQ列表', 'BW_list', 1),
    (NULL, 'private.blacklist', '[]', 'PRIVATE_LIST_SELECTOR', '{"listType":"private"}', '私聊黑名单', '用户QQ列表', 'BW_list', 1),
    (NULL, 'private.enable_list', 'true', 'BOOLEAN', NULL, '启用私聊黑白名单', '', 'BW_list', 1),
    (NULL, 'group.record_list', '[]', 'GROUP_LIST_SELECTOR', '{"listType":"group"}', '群聊消息记录名单', '名单内的群聊始终记录消息', 'message_record', 1),
    (NULL, 'private.record_list', '[]', 'PRIVATE_LIST_SELECTOR', '{"listType":"private"}', '私聊消息记录名单', '名单内的私聊始终记录消息', 'message_record', 1),
    (NULL, 'bot.markdown_to_txt', 'true', 'BOOLEAN', NULL, '将md格式转为纯文本发送', '', 'format', 1);
-- 上述列表示例：["123456789","1011121314"]，true为白名单，false为黑名单

-- 系统配置初始数据（全局配置，无 bot_id）
INSERT INTO system_configs (config_key, config_value, config_type, metadata, description, explain, category) VALUES
    ('system.name', 'MangoBot', 'STRING', NULL, '系统名称', '显示在界面上的系统名称', 'general'),
    ('system.version', '1.0.0', 'STRING', NULL, '系统版本', '当前系统版本号', 'general');
-- ============================================
-- 消息存储表
-- ============================================

CREATE TABLE IF NOT EXISTS group_messages
(
    id               INTEGER                                        not null
        constraint group_messages_pk
            primary key autoincrement,
    bot_id           TEXT                                           not null,
    group_id         TEXT                                           not null,
    message_id       TEXT,
    sender_id        TEXT,
    message_segments TEXT,
    message_time     INTEGER default (strftime('%s', 'now') * 1000) not null,
    is_delete        INTEGER DEFAULT 0,
    parse_message    TEXT
);

CREATE INDEX IF NOT EXISTS idx_group_messages_bot_group_time
    ON group_messages (bot_id, group_id, message_time);

CREATE INDEX IF NOT EXISTS group_messages_message_id_index
    ON group_messages (message_id);


CREATE TABLE IF NOT EXISTS private_messages(
    id               INTEGER                                        not null
        constraint private_messages_pk
            primary key autoincrement,
    bot_id           TEXT                                           not null,
    friend_id        TEXT                                           not null,
    message_id       TEXT,
    sender_id        TEXT,
    message_segments TEXT,
    message_time     INTEGER default (strftime('%s', 'now') * 1000) not null,
    is_delete        INTEGER DEFAULT 0,
    parse_message    TEXT
);

CREATE INDEX IF NOT EXISTS idx_private_messages_bot_group_time
    ON private_messages (bot_id, friend_id, message_time);

CREATE INDEX IF NOT EXISTS private_messages_message_id_index
    ON private_messages (message_id);


CREATE TABLE IF NOT EXISTS bot_files
(
    id             INTEGER not null
        constraint files_pk
            primary key autoincrement,
    file_type      TEXT,
    file_id        TEXT    not null
        constraint files_pk_2
            unique,
    url            TEXT,
    file_path      TEXT,
    sub_type       INTEGER,
    file_size      INTEGER,
    description    TEXT,
    create_time    INTEGER default (strftime('%s', 'now') * 1000)
);


CREATE TABLE IF NOT EXISTS plugins
(
    id                  INTEGER not null
        constraint plugins_pk
            primary key autoincrement,
    plugin_name         TEXT,
    jar_name            TEXT    not null
        constraint plugin_name_pk
            unique,
    author              TEXT    not null,
    version             TEXT,
    description         TEXT,
    enabled             INTEGER default 0,
    enabled_web         INTEGER default 0,
    package_name        TEXT,
    create_time         INTEGER default (strftime('%s', 'now') * 1000)
);


-- 对话会话表：记录每个工作区的对话会话
CREATE TABLE IF NOT EXISTS chat_session (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    bot_id TEXT,        -- Bot ID（使用 TEXT 兼容多平台）
    chat_id TEXT,       -- 关联群聊ID/私聊ID（使用 TEXT 兼容多平台）
    title VARCHAR(256), -- 会话标题（默认为该会话第一个问题，因此，前端点击新对话时，先不创建会话，等到输入问题并发送后再新建对话）
    source VARCHAR(32),
    custom_prompt TEXT, -- 定制人格提示词（非空表示已定制，覆盖该来源默认人格）
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 会话业务唯一键 (bot_id, chat_id, source)
CREATE UNIQUE INDEX IF NOT EXISTS idx_chat_session_unique
    ON chat_session (bot_id, chat_id, source);
CREATE INDEX IF NOT EXISTS idx_chat_session_bot_chat
    ON chat_session (bot_id, chat_id);

-- 对话消息表（统一 AI 对话消息投影表）
CREATE TABLE IF NOT EXISTS chat_messages (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id INTEGER NOT NULL, -- 关联的会话ID
    role VARCHAR(32) NOT NULL, -- 角色：user, assistant, system
    content TEXT NOT NULL, -- 消息内容
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (session_id) REFERENCES chat_session(id)
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_chat_messages_session ON chat_messages(session_id);
CREATE INDEX IF NOT EXISTS idx_chat_messages_time ON chat_messages(create_time);

-- ========================================
-- 1. Java 工具配置表
-- ========================================
CREATE TABLE agent_java_tool_config (
    id INTEGER PRIMARY KEY AUTOINCREMENT,

    -- 类信息
    class_name VARCHAR(500) NOT NULL UNIQUE,    -- 类全限定名（唯一约束）
    constructor_args TEXT,                       -- 构造参数 JSON
    load_type VARCHAR(20) DEFAULT 'NO_ARGS',    -- 加载方式：NO_ARGS, WITH_ARGS, INSTANCE, FACTORY

    -- 元数据（从 @MangoTool 注解读取，用于前端展示）
    tool_name VARCHAR(100),
    description TEXT,
    category VARCHAR(50),

    -- 来源（关联插件表，为空则表示系统内置）
    plugin_id INTEGER,

    -- 状态
    enabled BOOLEAN DEFAULT FALSE,

    -- 启用列表（当前启用的来源）
    enabled_list TEXT DEFAULT '["web","group","private"]',  -- JSON 数组: ["web", "group", "private"]

    -- 可启用列表（可以被启用的来源）
    available_list TEXT DEFAULT '["web","group","private"]',  -- JSON 数组: ["web", "group", "private"]

    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (plugin_id) REFERENCES plugins(id) ON DELETE SET NULL
);

-- ========================================
-- 2. MCP 连接配置表
-- ========================================
CREATE TABLE agent_mcp_config (
    id INTEGER PRIMARY KEY AUTOINCREMENT,

    mcp_name VARCHAR(100) NOT NULL,
    transport_type VARCHAR(20) NOT NULL,        -- STDIO, SSE, HTTP
    connection_config TEXT NOT NULL,            -- 连接参数 JSON

    connection_status INTEGER DEFAULT 0,        -- 状态（0: 断开, 1: 已连接, 2: 错误）
    enabled BOOLEAN DEFAULT FALSE,

    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- ========================================
-- 3. MCP 工具配置表
-- ========================================
CREATE TABLE agent_mcp_tool_config (
    id INTEGER PRIMARY KEY AUTOINCREMENT,

    mcp_config_id INTEGER NOT NULL,             -- 关联 MCP 连接表 id
    tool_name VARCHAR(100) NOT NULL,            -- MCP 工具名称

    -- 元数据（从 MCP 服务器获取）
    description TEXT,
    input_schema TEXT,

    -- 状态
    enabled BOOLEAN DEFAULT TRUE,

    -- 启用列表（当前启用的来源）
    enabled_list TEXT DEFAULT '["web","group","private"]',  -- JSON 数组: ["web", "group", "private"]

    -- 可启用列表（可以被启用的来源）
    available_list TEXT DEFAULT '["web","group","private"]',  -- JSON 数组: ["web", "group", "private"]

    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP,

    UNIQUE(mcp_config_id, tool_name),
    FOREIGN KEY (mcp_config_id) REFERENCES agent_mcp_config(id) ON DELETE CASCADE
);

-- ============================================
-- 平台连接配置表
-- ============================================

-- OneBot 平台配置表：存储 OneBot WebSocket 服务器配置（url 为唯一地址来源）
CREATE TABLE IF NOT EXISTS onebot_config (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    name            TEXT NOT NULL,                   -- 配置名称
    url             TEXT NOT NULL,                   -- WebSocket 服务器地址（ws://host:port/path）
    token           TEXT,                            -- 访问令牌（可选）
    protocol_type   TEXT NOT NULL,                   -- 协议类型：onebot_qq, telegram, discord 等
    enabled         INTEGER DEFAULT 0,               -- 是否启用：0-禁用, 1-启用
    connection_status INTEGER DEFAULT 0,             -- 连接状态：0-未启动, 1-运行中, 2-已停止, 3-错误
    description     TEXT,                            -- 描述
    created_at      INTEGER DEFAULT (strftime('%s', 'now') * 1000),
    updated_at      INTEGER DEFAULT (strftime('%s', 'now') * 1000)
);

-- OneBot 配置索引
CREATE INDEX IF NOT EXISTS idx_onebot_config_name ON onebot_config (name);
CREATE INDEX IF NOT EXISTS idx_onebot_config_enabled ON onebot_config (enabled);
CREATE INDEX IF NOT EXISTS idx_onebot_config_protocol ON onebot_config (protocol_type);

-- OneBot 配置初始数据
INSERT INTO onebot_config (name, url, token, protocol_type, enabled, description) VALUES
    ('默认配置', 'ws://0.0.0.0:8080', NULL, 'onebot_qq', 0, '默认 OneBot WebSocket 服务器配置');

-- ========================================
-- 4. Skill 配置表
-- ========================================
CREATE TABLE agent_skill_config (
    id INTEGER PRIMARY KEY AUTOINCREMENT,

    skill_name VARCHAR(100) NOT NULL,
    description TEXT,

    -- 文件路径（相对于 skills 目录）
    skill_path VARCHAR(500) NOT NULL UNIQUE,    -- 如: "weather_analysis"

    -- 绑定的工具 id 列表
    bound_tool_ids TEXT,                        -- JSON 数组: [1, 2, 3]

    -- 状态
    enabled BOOLEAN DEFAULT FALSE,

    -- 启用列表（当前启用的来源）
    enabled_list TEXT DEFAULT '["web","group","private"]',  -- JSON 数组: ["web", "group", "private"]

    -- 可启用列表（可以被启用的来源）
    available_list TEXT DEFAULT '["web","group","private"]',  -- JSON 数组: ["web", "group", "private"]

    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP
);


-- ============================================
-- 模拟对话台
-- ============================================

-- 一次模拟对话 = 一组消息 + 对它的打分。不存用例、不存指标、不存通过与否。
-- 注意：本项目没有迁移机制（SQLiteConfig 只在 DB 文件不存在时跑本文件），
-- 所以老库要靠 SimSchema.init() 里的 CREATE TABLE IF NOT EXISTS 兜底，
-- 两处定义必须逐字一致。
CREATE TABLE IF NOT EXISTS sim_session (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    title         TEXT,                       -- 人读的标题
    source        TEXT NOT NULL,              -- group / private
    bot_id        TEXT NOT NULL,              -- 被测虚拟 bot 的号（保留号段）
    chat_id       TEXT NOT NULL,              -- 虚拟群号 / 用户号（保留号段）
    persona_label TEXT,                       -- 这轮测的是哪版人格
    seed_ref      TEXT,                       -- 剧本的语料出处（只记来源）
    status        TEXT NOT NULL,              -- running / scoring / done / failed
    scores        TEXT,                       -- 各角度的分与原文证据（JSON 数组）
    verdict       TEXT,                       -- judge 的一句话总评
    create_time   DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sim_message (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id  INTEGER NOT NULL,             -- 属于哪一场
    seq         INTEGER NOT NULL,             -- 会话内顺序，从 1 开始
    speaker     TEXT,                         -- 显示名
    sender_id   TEXT,                         -- 发送者号
    role        TEXT NOT NULL,                -- peer（剧本喂的）/ bot（bot 发出来的）
    kind        TEXT NOT NULL,                -- text / image / meme
    content     TEXT,                         -- 正文；图片/表情包是 fileId
    reply_to    TEXT,                         -- bot 这条引用了哪条消息（没引用为 NULL）
    at_ms       INTEGER,                      -- 会话内相对时间（毫秒）
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_sim_message_session ON sim_message (session_id, seq);
