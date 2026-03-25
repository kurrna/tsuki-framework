package com.kurna.scan.proxy;

import com.kurna.tsuki.annotation.Autowired;
import com.kurna.tsuki.annotation.Component;

@Component
public class InjectProxyOnConstructorBean {

    public final OriginBean injected;

    public InjectProxyOnConstructorBean(@Autowired OriginBean injected) {
        this.injected = injected;
    }
}
