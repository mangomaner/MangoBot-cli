package io.github.mangomaner.mangobot.model.onebot.api;

import lombok.Data;

import java.util.Map;

/**
 * OneBot API 请求基类
 */
@Data
public class OneBotApiRequest {
    private String action;
    private Map<String, Object> params;
    private String echo;
}
