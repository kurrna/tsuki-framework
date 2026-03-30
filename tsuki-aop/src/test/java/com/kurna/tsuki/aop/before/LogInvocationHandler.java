package com.kurna.tsuki.aop.before;

import java.lang.reflect.Method;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kurna.tsuki.annotation.Component;
import com.kurna.tsuki.aop.BeforeInvocationHandlerAdapter;

@Component
public class LogInvocationHandler extends BeforeInvocationHandlerAdapter {

    final Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    public void before(Object proxy, Method method, Object[] args) {
        logger.info("[Before] {}()", method.getName());
    }
}
