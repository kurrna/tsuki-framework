package com.kurna.tsuki.aop.after;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Properties;

import com.kurna.tsuki.exception.ResourceScanException;
import org.junit.jupiter.api.Test;

import com.kurna.tsuki.context.AnnotationConfigApplicationContext;
import com.kurna.tsuki.io.PropertyResolver;

public class AfterProxyTest {

    @Test
    public void testAfterProxy() {
        try (var ctx = new AnnotationConfigApplicationContext(AfterApplication.class, createPropertyResolver())) {
            GreetingBean proxy = ctx.getBean(GreetingBean.class);
            // should change return value:
            assertEquals("Hello, Bob!", proxy.hello("Bob"));
            assertEquals("Morning, Alice!", proxy.morning("Alice"));
        } catch (ResourceScanException e) {
            throw new RuntimeException(e);
        }
    }

    PropertyResolver createPropertyResolver() {
        var ps = new Properties();
        return new PropertyResolver(ps);
    }
}
