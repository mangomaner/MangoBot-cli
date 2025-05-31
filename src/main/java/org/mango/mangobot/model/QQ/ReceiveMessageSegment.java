package org.mango.mangobot.model.QQ;

import lombok.Data;

@Data
public class ReceiveMessageSegment {
    private String type;
    private MessageData data;
    private String summary;
    private String file;
    private String file_id;
    private String file_size;
    private String path; // 添加这一行
}