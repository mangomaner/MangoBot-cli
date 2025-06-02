package org.mango.mangobot.annotation.QQ.method;

import org.mango.mangobot.annotation.QQ.QQMessageHandlerType;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@QQMessageHandlerType
public @interface ReplyMessage {}
