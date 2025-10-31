CREATE TABLE IF NOT EXISTS %s (
                                  id        integer not null
                                  constraint "primary"
                                  primary key autoincrement,
                                  user_id   integer,
                                  target_id integer,
                                  message   integer,
                                  timestamp integer
);

create index group_example_message_index
    on group_example (message);

create index table_name_user_id_index
    on group_example (user_id);