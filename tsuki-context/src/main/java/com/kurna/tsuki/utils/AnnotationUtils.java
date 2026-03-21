package com.kurna.tsuki.utils;

import com.kurna.tsuki.annotation.Bean;
import com.kurna.tsuki.annotation.Component;
import com.kurna.tsuki.exception.BeanDefinitionException;
import jakarta.annotation.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

public abstract class AnnotationUtils {

    /**
     * 递归查找Annotation<p></p>
     * 如 Spring 的可扩展注解 @Component 和 @Controller（后者包括前者）
     * <p>
     * 示例：Annotation A可以直接标注在Class定义:
     * <p>
     * <code>
     * &#064;A<br>public class Hello {}
     * </code>
     * <p>
     * 或者Annotation B标注了A，Class标注了B:
     * <p>
     * <code>
     * &#64;A<br>public @interface B {}
     * <p>
     * &#064;B<br>public class Hello {}
     * </code>
     *
     */
    public static <A extends Annotation> A findAnnotation(Class<?> target, Class<A> annoClass) {
        A a = target.getAnnotation(annoClass);
        for (Annotation anno : target.getAnnotations()) {
            Class<? extends Annotation> annoType = anno.annotationType();
            if (!annoType.getPackageName().equals("java.lang.annotation")) {
                A found = findAnnotation(annoType, annoClass);
                if (found != null) {
                    if (a != null) {
                        throw new BeanDefinitionException(
                            "Duplicate @" + annoClass.getSimpleName() +
                                " found on class " + target.getSimpleName()
                        );
                    }
                    a = found;
                }
            }
        }
        return a;
    }

    @Nullable
    @SuppressWarnings("unchecked")
    public static <A extends Annotation> A getAnnotation(Annotation[] annos, Class<A> annoClass) {
        for (Annotation anno : annos) {
            if (annoClass.isInstance(anno)) {
                return (A) anno;
            }
        }
        return null;
    }

    /**
     * 通过方法得到 Bean 的标识名
     * <p>
     * <code>
     * &#064;Bean<br>Hello createHello() {}
     * </code>
     */
    public static String getBeanName(Method method) {
        Bean bean = method.getAnnotation(Bean.class);
        String name = bean.value();
        if (name.isEmpty()) {
            name = method.getName();
        }
        return name;
    }

    /**
     * 通过类得到 Bean 的标识名
     * <p>
     * <code>
     * &#064;Component<br>public class Hello {}
     * </code>
     */
    public static String getBeanName(Class<?> clazz) {
        String name = "";
        // 查找@Component:
        Component component = clazz.getAnnotation(Component.class);
        if (component != null) {
            // @Component exist:
            name = component.value();
        } else {
            // 未找到@Component，继续在其他注解中查找@Component:
            for (Annotation anno : clazz.getAnnotations()) {
                if (findAnnotation(anno.annotationType(), Component.class) != null) {
                    try {
                        name = (String) anno.annotationType().getMethod("value").invoke(anno);
                    } catch (ReflectiveOperationException e) {
                        throw new BeanDefinitionException("Cannot get annotation value.", e);
                    }
                }
            }
        }
        if (name.isEmpty()) {
            // default name: "HelloWorld" => "helloWorld"
            name = clazz.getSimpleName();
            name = Character.toLowerCase(name.charAt(0)) + name.substring(1);
        }
        return name;
    }

    /**
     * Get non-arg method by @PostConstruct or @PreDestroy. Not search in super
     * class.
     * <p>
     * <code>
     * &#064;PostConstruct<br>void init() {}
     * </code>
     */
    @Nullable
    public static Method findAnnotationMethod(Class<?> clazz, Class<? extends Annotation> annoClass) {
        // try get declared method:
        List<Method> ms = Arrays.stream(clazz.getDeclaredMethods()).filter(m -> m.isAnnotationPresent(annoClass)).peek(m -> {
            if (m.getParameterCount() != 0) {
                throw new BeanDefinitionException(
                    String.format("Method '%s' with @%s must not have argument: %s", m.getName(), annoClass.getSimpleName(), clazz.getName()));
            }
        }).toList();
        if (ms.isEmpty()) {
            return null;
        }
        if (ms.size() == 1) {
            return ms.getFirst();
        }
        throw new BeanDefinitionException(String.format("Multiple methods with @%s found in class: %s", annoClass.getSimpleName(), clazz.getName()));
    }

    /**
     * Get non-arg method by method name. Not search in super class.
     */
    public static Method getNamedMethod(Class<?> clazz, String methodName) {
        try {
            return clazz.getDeclaredMethod(methodName);
        } catch (ReflectiveOperationException e) {
            throw new BeanDefinitionException(String.format("Method '%s' not found in class: %s", methodName, clazz.getName()));
        }
    }
}
