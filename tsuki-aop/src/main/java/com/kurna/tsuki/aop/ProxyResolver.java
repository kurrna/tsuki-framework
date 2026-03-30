package com.kurna.tsuki.aop;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.scaffold.subclass.ConstructorStrategy;
import net.bytebuddy.implementation.InvocationHandlerAdapter;
import net.bytebuddy.matcher.ElementMatchers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationHandler;

/**
 * 代理解析器，负责基于 ByteBuddy 为目标 Bean 创建运行时子类代理。
 * <p>
 * 当前实现采用简单单例模式对外提供统一入口：
 * 通过 {@link #createProxy(Object, InvocationHandler)} 拦截目标对象的 public 方法，
 * 并将方法调用委托给传入的 {@link InvocationHandler}。
 */
public class ProxyResolver {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * ByteBuddy 实例，用于动态生成代理类字节码。
     */
    protected ByteBuddy byteBuddy = new ByteBuddy();

    private static ProxyResolver instance = null;

    /**
     * 获取 {@link ProxyResolver} 单例。
     *
     * @return 代理解析器单例
     */
    public static ProxyResolver getInstance() {
        if (instance == null) {
            instance = new ProxyResolver();
        }
        return instance;
    }

    /**
     * 为指定 Bean 创建代理对象。
     * <p>
     * 代理对象为目标类型的子类，默认使用无参构造器实例化；
     * 代理会拦截所有 public 方法，并将调用转发给参数 {@code handler} 处理。
     *
     * @param bean 被代理的原始 Bean
     * @param handler 方法调用处理器
     * @param <T> Bean 类型
     * @return 生成的代理对象
     * @throws RuntimeException 当代理类实例化失败时抛出
     */
    @SuppressWarnings("unchecked")
    public <T> T createProxy(T bean, InvocationHandler handler) {
        // 目标 Bean 的 Class
        Class<?> targetClass = bean.getClass();
        logger.atDebug().log("create proxy for bean {} @{}", targetClass.getSimpleName(), Integer.toHexString(bean.hashCode()));
        // 动态创建 Proxy 的 Class
        // 外层 invoke 方法传入的 Object 是 Proxy 实例
        Class<?> proxyClass = this.byteBuddy
            // 子类默认无参数构造方法
            .subclass(targetClass, ConstructorStrategy.Default.DEFAULT_CONSTRUCTOR)
            // 拦截所有 public 方法
            .method(ElementMatchers.isPublic()).intercept(InvocationHandlerAdapter.of(
                // proxy 方法调用
                (proxy, method, args) -> {
                    // 内层的 invoke 方法将转发调用至原始 Bean
                    return handler.invoke(bean, method, args);
                }
            ))
            // 生成字节码
            .make()
            // 加载字节码
            .load(targetClass.getClassLoader()).getLoaded();


        //  生成目标代理类的实例
        Object proxy;
        try {
            proxy = proxyClass.getConstructor().newInstance();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return (T) proxy;
    }
}
