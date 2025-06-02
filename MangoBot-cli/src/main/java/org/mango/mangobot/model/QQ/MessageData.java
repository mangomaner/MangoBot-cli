package org.mango.mangobot.model.QQ;

import lombok.Data;

@Data
public class MessageData {
    private String text;
    private String file;
    private String url;
    private String qq;
    private String name;
    private String id;
    private String data;
    private Integer subType;
    private String file_size;
    private String path; // 添加这一行
    private String file_id;

    private String summary;
    private String emoji_id;
    private String emoji_package_id;
    private String key;
}