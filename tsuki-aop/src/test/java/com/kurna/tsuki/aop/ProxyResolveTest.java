package com.kurna.tsuki.aop;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;

public class ProxyResolveTest {

    @Test
    public void testProxyResolver() {
        OriginBean origin = new OriginBean();
        origin.name = "Bob";

        assertEquals("Hello, Bob.", origin.hello());

        // create proxy
        OriginBean proxy = new ProxyResolver().createProxy(origin, new PoliteInvocationHandler());

        // Proxy 类名，类似 OriginBean$ByteBuddy$...
        System.out.println(proxy.getClass().getSimpleName());

        // proxy class
        assertNotSame(OriginBean.class, proxy.getClass());
        assertNull(proxy.name);

        // 带 @Polite
        assertEquals("Hello, Bob!", proxy.hello());
        // 不带 @Polite
        assertEquals("Morning, Bob.", proxy.morning());
    }
}
