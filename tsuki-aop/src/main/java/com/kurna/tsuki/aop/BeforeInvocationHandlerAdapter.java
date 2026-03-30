package com.kurna.tsuki.aop;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

/**
 * 前置增强适配器。
 * <p>
 * 子类仅需实现 {@link #before(Object, Method, Object[])}，即可在目标方法执行前插入增强逻辑。
 */
public abstract class BeforeInvocationHandlerAdapter implements InvocationHandler {

    /**
     * 在目标方法执行前触发。
     *
     * @param proxy 目标对象
     * @param method 即将调用的方法
     * @param args 方法参数
     */
    public abstract void before(Object proxy, Method method, Object[] args);

    /**
     * 模板方法：先执行前置增强，再调用目标方法。
     *
     * @param proxy 目标对象
     * @param method 目标方法
     * @param args 方法参数
     * @return 目标方法返回值
     * @throws Throwable 反射调用或增强过程中的异常
     */
    @Override
    public final Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        before(proxy, method, args);
        return method.invoke(proxy, args);
    }
}
