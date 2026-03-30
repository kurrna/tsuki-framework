package com.kurna.tsuki.aop.around;

import com.kurna.tsuki.annotation.Around;
import com.kurna.tsuki.annotation.Component;
import com.kurna.tsuki.annotation.Value;

@Component
@Around("aroundInvocationHandler")
public class OriginBean {

    @Value("${customer.name}")
    public String name;

    @Polite
    public String hello() {
        return "Hello, " + name + ".";
    }

    public String morning() {
        return "Morning, " + name + ".";
    }
}
