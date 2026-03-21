package com.kurna.tsuki.utils;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.kurna.tsuki.annotation.Component;
import com.kurna.tsuki.annotation.Configuration;
import com.kurna.tsuki.annotation.Order;
import com.kurna.tsuki.exception.BeanDefinitionException;

public class AnnotationUtilsTest {

    @Test
    public void noComponent() {
        assertNull(AnnotationUtils.findAnnotation(Simple.class, Component.class));
    }

    @Test
    public void simpleComponent() {
        assertNotNull(AnnotationUtils.findAnnotation(SimpleComponent.class, Component.class));
        assertEquals("simpleComponent", AnnotationUtils.getBeanName(SimpleComponent.class));
    }

    @Test
    public void simpleComponentWithName() {
        assertNotNull(AnnotationUtils.findAnnotation(SimpleComponentWithName.class, Component.class));
        assertEquals("simpleName", AnnotationUtils.getBeanName(SimpleComponentWithName.class));
    }

    @Test
    public void simpleConfiguration() {
        assertNotNull(AnnotationUtils.findAnnotation(SimpleConfiguration.class, Component.class));
        assertEquals("simpleConfiguration", AnnotationUtils.getBeanName(SimpleConfiguration.class));
    }

    @Test
    public void simpleConfigurationWithName() {
        assertNotNull(AnnotationUtils.findAnnotation(SimpleConfigurationWithName.class, Component.class));
        assertEquals("simpleCfg", AnnotationUtils.getBeanName(SimpleConfigurationWithName.class));
    }

    @Test
    public void customComponent() {
        assertNotNull(AnnotationUtils.findAnnotation(Custom.class, Component.class));
        assertEquals("custom", AnnotationUtils.getBeanName(Custom.class));
    }

    @Test
    public void customComponentWithName() {
        assertNotNull(AnnotationUtils.findAnnotation(CustomWithName.class, Component.class));
        assertEquals("customName", AnnotationUtils.getBeanName(CustomWithName.class));
    }

    @Test
    public void duplicateComponent() {
        assertThrows(BeanDefinitionException.class, () -> AnnotationUtils.findAnnotation(DuplicateComponent.class, Component.class));
        assertThrows(BeanDefinitionException.class, () -> AnnotationUtils.findAnnotation(DuplicateComponent2.class, Component.class));
    }
}

@Order(1)
class Simple {
}

@Component
class SimpleComponent {
}

@Component("simpleName")
class SimpleComponentWithName {
}

@Configuration
class SimpleConfiguration {

}

@Configuration("simpleCfg")
class SimpleConfigurationWithName {

}

@CustomComponent
class Custom {

}

@CustomComponent("customName")
class CustomWithName {

}

@Component
@Configuration
class DuplicateComponent {

}

@CustomComponent
@Configuration
class DuplicateComponent2 {

}
