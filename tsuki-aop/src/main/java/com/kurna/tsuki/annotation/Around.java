package com.kurna.tsuki.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented

public @interface Around {

    /**
     * 代理处理器 Bean 名称。
     * <p>
     * 该 Bean 必须实现 {@link java.lang.reflect.InvocationHandler}。
     *
     * @return 处理器 Bean 名称
     */
    String value();

}
