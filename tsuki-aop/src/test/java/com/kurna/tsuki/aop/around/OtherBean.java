package com.kurna.tsuki.aop.around;

import com.kurna.tsuki.annotation.Autowired;
import com.kurna.tsuki.annotation.Component;
import com.kurna.tsuki.annotation.Order;

@Order(0)
@Component
public class OtherBean {

    public OriginBean origin;

    public OtherBean(@Autowired OriginBean origin) {
        this.origin = origin;
    }
}
