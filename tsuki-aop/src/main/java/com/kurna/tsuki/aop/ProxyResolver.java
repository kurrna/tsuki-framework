package com.kurna.tsuki.aop;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.scaffold.subclass.ConstructorStrategy;
import net.bytebuddy.implementation.InvocationHandlerAdapter;
import net.bytebuddy.matcher.ElementMatchers;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

public class ProxyResolver {

    ByteBuddy byteBuddy = new ByteBuddy();

    @SuppressWarnings("unchecked")
    public <T> T createProxy(T bean, InvocationHandler handler) {
        // 目标 Bean 的 Class
        Class<?> targetClass = bean.getClass();
        // 动态创建 Proxy 的 Class
        Class<?> proxyClass = this.byteBuddy
            // 子类默认无参数构造方法
            .subclass(targetClass, ConstructorStrategy.Default.DEFAULT_CONSTRUCTOR)
            // 拦截所有 public 方法
            .method(ElementMatchers.isPublic()).intercept(InvocationHandlerAdapter.of(
                // proxy 方法调用
                new InvocationHandler() {
                    // 外层 invoke 方法传入的 Object 是 Proxy 实例
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        // 内层的 invoke 方法将转发调用至原始 Bean
                        return handler.invoke(bean, method, args);
                    }
                }
            ))
            // 生成字节码
            .make()
            // 加载字节码
            .load(targetClass.getClassLoader()).getLoaded();

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
