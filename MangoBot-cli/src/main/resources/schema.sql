-- MangoBot 数据库初始化脚本
-- 创建时间: 2026-01-17

CREATE TABLE IF NOT EXISTS mangobot_config(
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    config_key TEXT NOT NULL UNIQUE,      -- 配置项唯一键，如 'bot.token', 'server.port'
    config_value TEXT,                    -- 配置值（字符串存储，支持 JSON）
    config_type TEXT NOT NULL,            -- 类型：STRING, INTEGER, BOOLEAN, JSON, SELECT
    description TEXT,                     -- 描述（用于前端展示）
    explain TEXT,                         -- 配置项说明
    category TEXT DEFAULT 'general',      -- 分类：bot / server / webhook 等
    editable BOOLEAN DEFAULT 1,           -- 是否允许前端修改（0-不允许, 1-允许）
    created_at INTEGER DEFAULT (strftime('%s', 'now') * 1000),   -- 毫秒时间戳
    updated_at INTEGER DEFAULT (strftime('%s', 'now') * 1000)
);

INSERT INTO mangobot_config (config_key, config_value, config_type, description, explain, category, editable) VALUES
    -- 配置群组白名单、黑名单和启用黑/白名单 三项配置
    ('main.QQ.group.whitelist', '{}', 'JSON', '群组白名单', 'key为QQ号(long)，value为long列表。示例：{"1234567890": [12421412,4263153]}', 'BW_list', 1),
    ('main.QQ.group.blacklist', '{}', 'JSON', '群组黑名单', 'key为QQ号(long)，value为long列表。示例：{"1234567890": [12421412,4263153]}', 'BW_list', 1),
    ('main.QQ.group.enable_list', '0', 'BOOLEAN', '启用黑/白名单', '', 'BW_list', 1),

    -- 配置群组白名单、黑名单和启用黑/白名单 三项配置
    ('main.QQ.private.whitelist', '{}', 'JSON', '私聊白名单', 'key为QQ号(long)，value为long列表。示例：{"1234567890": [12421412,4263153]}', 'BW_list', 1),
    ('main.QQ.private.blacklist', '{}', 'JSON', '私聊黑名单', 'key为QQ号(long)，value为long列表。示例：{"1234567890": [12421412,4263153]}', 'BW_list', 1),
    ('main.QQ.private.enable_list', '0', 'BOOLEAN', '启用黑/白名单', '', 'BW_list', 1),

    -- 配置大模型
    ('main.model.main_model', '{"base_url": "https://xxx.xxxxx.xx/xx", "api-key": "sk-xxx", "model_name": "xxx"}', 'JSON', '主模型', '', 'model', 1),
    ('main.model.assistant_model', '{"base_url": "https://xxx.xxxxx.xx/xx", "api-key": "sk-xxx", "model_name": "xxx"}', 'JSON', '助手模型（用量较大，用于简单任务）', '', 'model', 1),
    ('main.model.image_model', '{"base_url": "https://xxx.xxxxx.xx/xx", "api-key": "sk-xxx", "model_name": "xxx"}', 'JSON', '图片模型', '', 'model', 1),

    -- 主项目普通配置
    ('main.QQ.test', '好好好', 'STRING', '测试配置', '', 'normal', 1),
    ('main.QQ.select', '["小","中","大"]', 'SELECT', '测试配置', '', 'normal', 1),

    -- 插件配置
    ('plugin.example.test', '{}', 'JSON', '测试用', '测试', 'example', 1);


CREATE TABLE IF NOT EXISTS group_messages
(
    id               INTEGER                                        not null
        constraint group_messages_pk
            primary key autoincrement,
    bot_id           INTEGER                                        not null,
    group_id         INTEGER                                        not null,
    message_id       INTEGER,
    sender_id        INTEGER,
    message_segments TEXT,                                                      -- 消息段
    message_time     INTEGER default (strftime('%s', 'now') * 1000) not null,
    is_delete        INTEGER DEFAULT 0,
    -- 上方是消息原始内容，以下为加工后的内容
    parse_message    TEXT                                                       -- 解析后的自然语言消息（供参考）
);

-- 创建联合索引：bot_id → group_id → message_time，模仿使用者视角构建B树，用于快速检索
CREATE INDEX IF NOT EXISTS idx_group_messages_bot_group_time
    ON group_messages (bot_id, group_id, message_time);

-- message_id 索引（用于快速查单条消息）
CREATE INDEX IF NOT EXISTS group_messages_message_id_index
    ON group_messages (message_id);


CREATE TABLE IF NOT EXISTS private_messages(
    id               INTEGER                                        not null
        constraint private_messages_pk
            primary key autoincrement,
    bot_id           INTEGER                                        not null,
    friend_id        INTEGER                                        not null,
    message_id       INTEGER,
    sender_id        INTEGER,
    message_segments TEXT,                                                      -- 消息段
    message_time     INTEGER default (strftime('%s', 'now') * 1000) not null,
    is_delete        INTEGER DEFAULT 0,
    -- 上方是消息原始内容，以下为加工后的内容
    parse_message    TEXT                                                       -- 解析后的自然语言消息（供参考）
);

-- 创建联合索引：bot_id → friend_id → message_time，模仿使用者视角构建B树，用于快速检索
CREATE INDEX IF NOT EXISTS idx_private_messages_bot_group_time
    ON private_messages (bot_id, friend_id, message_time);

-- message_id 索引（用于快速查单条消息）
CREATE INDEX IF NOT EXISTS private_messages_message_id_index
    ON private_messages (message_id);


CREATE TABLE IF NOT EXISTS files
(
    id             INTEGER not null
        constraint files_pk
            primary key autoincrement,
    file_type      TEXT,                    -- 文件类型
    file_id        TEXT    not null         -- 文件ID
        constraint files_pk_2
            unique,
    url            TEXT,
    file_path      TEXT,                    -- 文件相对路径
    sub_type       INTEGER,                 -- 图片子类型
    file_size      INTEGER,
    description    TEXT,                    -- 文件描述
    create_time    INTEGER default (strftime('%s', 'now') * 1000)
);

