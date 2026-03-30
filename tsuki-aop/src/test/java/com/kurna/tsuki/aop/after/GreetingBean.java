package com.kurna.tsuki.aop.after;

import com.kurna.tsuki.annotation.Around;
import com.kurna.tsuki.annotation.Component;

@Component
@Around("politeInvocationHandler")
public class GreetingBean {

    public String hello(String name) {
        return "Hello, " + name + ".";
    }

    public String morning(String name) {
        return "Morning, " + name + ".";
    }
}
