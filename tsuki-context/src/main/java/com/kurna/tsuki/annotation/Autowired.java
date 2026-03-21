package com.kurna.tsuki.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * &#064;Target 注解表示 &#064;Autowired 可以用在字段、方法和参数上。<br>
 * &#064;Retention 注解表示 &#064;Autowired 在运行时仍然可用。<br>
 * &#064;Documented 注解表示 &#064;Autowired 将包含在 Javadoc 中。<br>
 * &#064;Autowired 注解表示一个依赖关系，Spring 将自动注入满足该依赖关系的 Bean。<br>
 * value 属性表示是否必须满足该依赖关系，默认值为 true。<br>
 * name 属性表示要注入的 Bean 的名称，默认为空字符串，表示根据类型自动注入。
 */
@Target({ ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Autowired {

    boolean value() default true;

    String name() default "";
}
