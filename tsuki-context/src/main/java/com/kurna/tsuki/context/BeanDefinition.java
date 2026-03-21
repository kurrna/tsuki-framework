package com.kurna.tsuki.context;

import com.kurna.tsuki.exception.BeanCreationException;
import jakarta.annotation.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Objects;

/**
 * 同时存储方法和方法名
 * <p>
 * 因为在 @Component 声明的 Bean 中可以根据 @PostConstruct 或 @PreDestory
 * 拿到方法<br>
 * 而在 @Bean 声明的 Bean 中，无法拿到方法，只能通过 @Bean 注解提取出字符串格式
 * 的方法名称<br>
 * 所以方法名和方法必有一个为 null
 * </p>
 */
public class BeanDefinition implements Comparable<BeanDefinition> {

    // 标识名
    String name;

    // 声明类型
    Class<?> beanClass;

    // Bean的实例
    Object instance = null;

    // 构造方法/null
    Constructor<?> constructor;

    // 工厂方法名/null
    String factoryMethodName;

    // 工厂方法/null
    Method factoryMethod;

    // Bean 的顺序
    int order;

    // 是否表示 @Primary
    boolean isPrimary;

    // init/destroy 方法名
    String initMethodName;
    String destroyMethodName;

    // init/destroy 方法
    Method initMethod;
    Method destroyMethod;

    public BeanDefinition(String name, Class<?> beanClass,
                          Constructor<?> constructor,
                          int order, boolean isPrimary, String initMethodName,
                          String destroyMethodName, Method initMethod, Method destroyMethod) {
        this.name = name;
        this.beanClass = beanClass;
        this.constructor = constructor;
        this.factoryMethodName = null;
        this.factoryMethod = null;
        this.order = order;
        this.isPrimary = isPrimary;
        constructor.setAccessible(true);
        setInitAndDestroyMethod(initMethodName, destroyMethodName, initMethod, destroyMethod);
    }

    public BeanDefinition(String name, Class<?> beanClass,
                          String factoryName, Method factoryMethod,
                          int order, boolean isPrimary, String initMethodName,
                          String destroyMethodName, Method initMethod, Method destroyMethod) {
        this.name = name;
        this.beanClass = beanClass;
        this.constructor = null;
        this.factoryMethodName = factoryName;
        this.factoryMethod = factoryMethod;
        this.order = order;
        this.isPrimary = isPrimary;
        factoryMethod.setAccessible(true);
        setInitAndDestroyMethod(initMethodName, destroyMethodName, initMethod, destroyMethod);
    }

    private void setInitAndDestroyMethod(String initMethodName, String destroyMethodName,
                                         Method initMethod, Method destroyMethod) {
        this.initMethodName = initMethodName;
        this.destroyMethodName = destroyMethodName;
        if (initMethod != null) {
            initMethod.setAccessible(true);
        }
        if (destroyMethod != null) {
            destroyMethod.setAccessible(true);
        }
        this.initMethod = initMethod;
        this.destroyMethod = destroyMethod;
    }

    @Nullable
    public Constructor<?> getConstructor() {
        return this.constructor;
    }

    @Nullable
    public String getFactoryMethodName() {
        return this.factoryMethodName;
    }

    @Nullable
    public Method getFactoryMethod() {
        return this.factoryMethod;
    }

    @Nullable
    public Method getInitMethod() {
        return this.initMethod;
    }

    @Nullable
    public Method getDestroyMethod() {
        return this.destroyMethod;
    }

    @Nullable
    public String getInitMethodName() {
        return this.initMethodName;
    }

    @Nullable
    public String getDestroyMethodName() {
        return this.destroyMethodName;
    }

    public String getName() {
        return this.name;
    }

    public Class<?> getBeanClass() {
        return this.beanClass;
    }

    @Nullable
    public Object getInstance() {
        return this.instance;
    }

    public Object getRequiredInstance() {
        if (this.instance == null) {
            throw new BeanCreationException(String.format("Instance of bean with name '%s' and type '%s' is not instantiated during current stage.",
                this.getName(), this.getBeanClass().getName()));
        }
        return this.instance;
    }

    public void setInstance(Object instance) {
        Objects.requireNonNull(instance, "Bean instance is null.");
        if (!this.beanClass.isAssignableFrom(instance.getClass())) {
            throw new BeanCreationException(String.format("Instance '%s' of Bean '%s' is not the expected type: %s", instance, instance.getClass().getName(),
                this.beanClass.getName()));
        }
        this.instance = instance;
    }

    public boolean isPrimary() {
        return this.isPrimary;
    }

    @Override
    public String toString() {
        return "BeanDefinition [name=" + name + ", beanClass=" + beanClass.getName() + ", factory=" + getCreateDetail() + ", init-method="
            + (initMethod == null ? "null" : initMethod.getName()) + ", destroy-method=" + (destroyMethod == null ? "null" : destroyMethod.getName())
            + ", isPrimary=" + isPrimary + ", instance=" + instance + "]";
    }

    String getCreateDetail() {
        if (this.factoryMethod != null) {
            String params = String.join(", ",
                Arrays.stream(this.factoryMethod.getParameterTypes()).
                    map(Class::getSimpleName).toArray(String[]::new));
            return this.factoryMethod.getDeclaringClass().getSimpleName() + "." +
                this.factoryMethod.getName() + "(" + params + ")";
        }
        return null;
    }

    @Override
    public int compareTo(BeanDefinition def) {
        int cmp = Integer.compare(this.order, def.order);
        if (cmp != 0) {
            return cmp;
        }
        return this.name.compareTo(def.name);
    }
}
