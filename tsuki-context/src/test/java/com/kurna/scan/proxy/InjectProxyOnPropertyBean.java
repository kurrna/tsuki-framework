package com.kurna.scan.proxy;

import com.kurna.tsuki.annotation.Autowired;
import com.kurna.tsuki.annotation.Component;

@Component
public class InjectProxyOnPropertyBean {

    @Autowired
    public OriginBean injected;
}
