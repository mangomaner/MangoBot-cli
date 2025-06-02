package org.mango.mangobot.QQ.method;

import org.mango.mangobot.QQ.QQMessageHandlerType;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@QQMessageHandlerType
public @interface ReplyMessage {}
