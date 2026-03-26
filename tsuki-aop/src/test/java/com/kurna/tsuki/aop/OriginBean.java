package com.kurna.tsuki.aop;

public class OriginBean {

    String name;

    @Polite
    public String hello() {
        return "Hello, " + this.name + ".";
    }

    public String morning() {
        return "Morning, " + this.name + ".";
    }
}
