package com.kurna.scan.nested;

import com.kurna.tsuki.annotation.Component;

@Component
public class OuterBean {

    @Component
    public static class NestedBean {

    }
}
