package com.kurna.tsuki.aop.before;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Properties;

import com.kurna.tsuki.exception.ResourceScanException;
import org.junit.jupiter.api.Test;

import com.kurna.tsuki.context.AnnotationConfigApplicationContext;
import com.kurna.tsuki.io.PropertyResolver;

public class BeforeProxyTest {

    @Test
    public void testBeforeProxy() {
        try (var ctx = new AnnotationConfigApplicationContext(BeforeApplication.class, createPropertyResolver())) {
            BusinessBean proxy = ctx.getBean(BusinessBean.class);
            // should print log:
            assertEquals("Hello, Bob.", proxy.hello("Bob"));
            assertEquals("Morning, Alice.", proxy.morning("Alice"));
        } catch (ResourceScanException e) {
            throw new RuntimeException(e);
        }
    }

    PropertyResolver createPropertyResolver() {
        var ps = new Properties();
        return new PropertyResolver(ps);
    }
}
