package com.kurna.tsuki.aop;

import com.kurna.tsuki.annotation.Around;

/**
 * {@link Around} 注解专用的代理后置处理器。
 * <p>
 * 该实现不新增行为，仅通过泛型参数将
 * {@link AnnotationProxyBeanPostProcessor} 绑定到 {@link Around} 注解。
 */
public class AroundProxyBeanPostProcessor extends AnnotationProxyBeanPostProcessor<Around> {
}
