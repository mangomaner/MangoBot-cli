-- auto-generated definition
create table if not exists chat_message
(
    id        INTEGER
        primary key autoincrement,
    message_id INTEGER,
    group_id  TEXT,
    user_id   TEXT,
    target_id TEXT,
    message   TEXT,
    image_url TEXT,
    timestamp datetime default CURRENT_DATE
);

create index idx_message_id
    on chat_message (message_id);

create index idx_group
    on chat_message (group_id);

create index idx_message
    on chat_message (message);

create index idx_time
    on chat_message (timestamp);

