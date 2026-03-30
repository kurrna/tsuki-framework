package com.kurna.tsuki.aop;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

/**
 * 后置增强适配器。
 * <p>
 * 子类可在目标方法执行后对返回值做二次处理，或执行日志、审计等横切逻辑。
 */
public abstract class AfterInvocationHandlerAdapter implements InvocationHandler {

    /**
     * 在目标方法执行后触发。
     *
     * @param proxy 目标对象
     * @param returnValue 目标方法原始返回值
     * @param method 目标方法
     * @param args 方法参数
     * @return 增强后的返回值
     */
    public abstract Object after(Object proxy, Object returnValue, Method method, Object[] args);

    /**
     * 模板方法：先调用目标方法，再执行后置增强。
     *
     * @param proxy 目标对象
     * @param method 目标方法
     * @param args 方法参数
     * @return 后置增强后的返回值
     * @throws Throwable 反射调用或增强过程中的异常
     */
    @Override
    public final Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        Object ret = method.invoke(proxy, args);
        return after(proxy, ret, method, args);
    }
}