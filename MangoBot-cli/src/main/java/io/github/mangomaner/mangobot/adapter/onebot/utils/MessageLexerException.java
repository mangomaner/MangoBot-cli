package io.github.mangomaner.mangobot.adapter.onebot.utils;

/**
 * {@link MessageLexer#parse(String)} 解析模型字面文本失败时抛出。
 * 调用方（发送工具）应捕获并作为错误回传给模型，触发重试。
 */
public class MessageLexerException extends IllegalArgumentException {

    public MessageLexerException(String message) {
        super(message);
    }
}
