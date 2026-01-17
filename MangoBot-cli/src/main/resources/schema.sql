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
    ('main.QQ.test', '["小","中","大"]', 'SELECT', '测试配置', '', 'normal', 1),

    -- 插件配置
    ('plugin.example.test', '{}', 'JSON', '测试用', '测试', 'example', 1)
    ;