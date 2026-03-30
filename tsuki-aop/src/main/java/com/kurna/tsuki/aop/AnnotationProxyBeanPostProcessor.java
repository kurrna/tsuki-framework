package com.kurna.tsuki.aop;

import com.kurna.tsuki.context.BeanDefinition;
import com.kurna.tsuki.context.BeanPostProcessor;
import com.kurna.tsuki.context.ConfigurableApplicationContext;
import com.kurna.tsuki.exception.AopConfigException;
import com.kurna.tsuki.utils.ApplicationContextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

/**
 * 基于类级注解创建代理的 Bean 后置处理器抽象基类。
 * <p>
 * 子类通过泛型参数声明目标注解类型（如 {@code @Around}），
 * 当某个 Bean 类型标注了该注解时，容器会在初始化前创建代理对象并替换原始 Bean。
 * 为保证属性注入等流程仍作用于原始对象，本类会缓存原始 Bean 并在属性注入阶段回退。
 *
 * @param <A> 触发代理创建的注解类型
 */
public abstract class AnnotationProxyBeanPostProcessor<A extends Annotation> implements BeanPostProcessor {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * 保存已被代理替换的原始 Bean，key 为 Bean 名称。
     */
    final Map<String, Object> originBeans = new HashMap<>();

    /**
     * 子类声明的注解类型，用于运行时查找类级注解。
     */
    final Class<A> annotationClass;

    /**
     * 构造时解析子类泛型参数，确定目标注解类型。
     */
    public AnnotationProxyBeanPostProcessor() {
        this.annotationClass = getParameterizedType();
    }

    /**
     * 在 Bean 初始化前检查是否存在目标注解，必要时创建代理。
     *
     * @param bean 当前 Bean 实例
     * @param beanName Bean 名称
     * @return 若命中注解则返回代理对象，否则返回原始 Bean
     * @throws AopConfigException 当注解未定义 {@code String value()} 或代理处理器配置错误时抛出
     */
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        Class<?> beanClass = bean.getClass();

        // 是否有 Class 级别的 注解
        A annotation = beanClass.getAnnotation(annotationClass);
        if (annotation != null) {
            String handlerName;
            try {
                handlerName = (String) annotation.annotationType().getMethod("value").invoke(annotation);
            } catch (ReflectiveOperationException e) {
                throw new AopConfigException(String.format("annotation %s must have a String value() method", annotationClass.getName()));
            }
            Object proxy = createProxy(bean, handlerName);
            originBeans.put(beanName, bean);
            return proxy;
        } else {
            return bean;
        }
    }

    /**
     * 根据注解中的处理器 Bean 名称创建代理。
     *
     * @param bean 被代理 Bean 实例
     * @param handlerName 代理处理器 Bean 名称
     * @return 代理对象
     * @throws AopConfigException 当处理器 Bean 不存在或不是 {@link InvocationHandler} 类型时抛出
     */
    Object createProxy(Object bean, String handlerName) {
        Object handlerBean = getBeanDef(handlerName);
        // 若处理器是 InvocationHandler 类型，则调用 ProxyResolver 创建代理，否则抛出异常
        if (handlerBean instanceof InvocationHandler handler) {
            return ProxyResolver.getInstance().createProxy(bean, handler);
        } else {
            throw new AopConfigException(String.format("@%s proxy handler '%s' is not type of %s.", this.annotationClass.getSimpleName(), handlerName,
                InvocationHandler.class.getName()));
        }
    }

    /**
     * 通过 Bean 名称获取实例，若不存在实例则调用 {@code createBeanAsEarlySingleton} 创建实例
     *
     * @param beanName Bean 的名称
     * @return Bean 的实例
     * @throws AopConfigException 当 aop 处理器 Bean 定义不存在时抛出
     */
    private Object getBeanDef(String beanName) {
        ConfigurableApplicationContext ctx = (ConfigurableApplicationContext) ApplicationContextUtils.getRequiredApplicationContext();
        // 查找 Bean 定义
        BeanDefinition beanDef = ctx.findBeanDefinition(beanName);
        if (beanDef == null) {
            throw new AopConfigException(String.format("@%s proxy handler '%s' not found.", this.annotationClass.getSimpleName(), beanName));
        }
        // 获取 Bean 实例，若不存在则创建一个早期单例
        Object beanInstance = beanDef.getInstance();
        if (beanInstance == null) {
            beanInstance = ctx.createBeanAsEarlySingleton(beanDef);
        }
        return beanInstance;
    }

    /**
     * 在属性注入阶段返回原始 Bean，避免对代理对象执行字段/方法注入。
     *
     * @param bean 当前 Bean（可能是代理）
     * @param beanName Bean 名称
     * @return 若存在缓存则返回原始 Bean，否则返回当前 Bean
     */
    @Override
    public Object postProcessOnSetProperty(Object bean, String beanName) {
        Object origin = this.originBeans.get(beanName);
        if (origin != null) {
            logger.debug("auto set property for {} from proxy {} to origin bean: {}", beanName, bean, origin);
            return origin;
        }
        return bean;
    }

    /**
     * 获取子类写在尖括号里的泛型类型，返回它的 Class 对象。如 @Around 等
     *
     * @return 注解类型
     * @throws IllegalArgumentException 当泛型参数数量或类型不符合预期时抛出
     */
    @SuppressWarnings("unchecked")
    private Class<A> getParameterizedType() {
        // 获取当前类带泛型的父类信息
        Type type = getClass().getGenericSuperclass();
        if (!(type instanceof ParameterizedType pt)) {
            throw new IllegalArgumentException("Class " + getClass().getSimpleName() + " does not have parameterized type.");
        }
        Type[] types = pt.getActualTypeArguments();
        if (types.length != 1) {
            throw new IllegalArgumentException("Class " + getClass().getSimpleName() + " has more than 1 parameterized types.");
        }
        Type r = types[0];
        if (!(r instanceof Class<?>)) {
            throw new IllegalArgumentException("Class " + getClass().getSimpleName() + " does not have parameterized type of class.");
        }
        return (Class<A>) r;
    }
}
