package com.kurna.tsuki.context;

import com.kurna.tsuki.annotation.Bean;
import com.kurna.tsuki.annotation.Component;
import com.kurna.tsuki.annotation.ComponentScan;
import com.kurna.tsuki.annotation.Configuration;
import com.kurna.tsuki.annotation.Import;
import com.kurna.tsuki.annotation.Order;
import com.kurna.tsuki.annotation.Primary;
import com.kurna.tsuki.exception.BeanCreationException;
import com.kurna.tsuki.exception.BeanDefinitionException;
import com.kurna.tsuki.exception.BeanNotOfRequiredTypeException;
import com.kurna.tsuki.exception.NoUniqueBeanDefinitionException;
import com.kurna.tsuki.exception.ResourceScanException;
import com.kurna.tsuki.io.PropertyResolver;
import com.kurna.tsuki.io.ResourceResolver;
import com.kurna.tsuki.utils.AnnotationUtils;
import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 基于注解的应用上下文实现。
 * <p>
 * 该上下文负责：
 * <ul>
 *   <li>扫描指定包下的 class 资源；</li>
 *   <li>识别并解析 {@code @Component}/{@code @Configuration}/{@code @Bean}；</li>
 *   <li>构建 {@link BeanDefinition} 并提供按名称、按类型的查询能力。</li>
 * </ul>
 * </p>
 */
public class AnnotationConfigApplicationContext {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    private final Map<String, BeanDefinition> beans;

    private final PropertyResolver propertyResolver;

    /**
     * 根据配置类创建应用上下文并完成 BeanDefinition 扫描与解析。
     *
     * @param configClass 配置类（用于确定扫描包与 {@code @Import} 入口）
     * @param propertyResolver 属性解析器
     * @throws ResourceScanException 当包资源扫描失败时抛出
     */
    public AnnotationConfigApplicationContext(Class<?> configClass, PropertyResolver propertyResolver)
        throws ResourceScanException {
        this.propertyResolver = propertyResolver;
        // 扫描获取所有的 Bean 的 Class
        Set<String> beanClassNames = scanForBeanClassNames(configClass);

        // 创建 Bean 的定义
        this.beans = createBeanDefinitions(beanClassNames);
    }

    /**
     * 扫描配置类指定的包路径，收集候选 Bean 的类名集合。
     *
     * @param configClass 配置类
     * @return 扫描得到的 class 全限定名（包含 {@code @Import} 导入类）
     * @throws ResourceScanException 资源扫描异常
     */
    Set<String> scanForBeanClassNames(Class<?> configClass) throws ResourceScanException {
        // 获取 @ComponentScan 注解
        ComponentScan scan = AnnotationUtils.findAnnotation(configClass, ComponentScan.class);
        // 获取注解配置的 package 名，未配置则默认当前类所在包
        String[] scanPackages = scan == null || scan.value().length == 0 ?
            new String[]{configClass.getPackage().getName()} : scan.value();

        Set<String> classNameSet = new HashSet<>();
        for (String pkg : scanPackages) {
            logger.atDebug().log("scan package: {}", pkg);
            // 扫描一个包
            var rr = new ResourceResolver(pkg);
            List<String> classList = rr.scan(res -> {
                String name = res.name();
                if (name.endsWith(".class")) {
                    return name.substring(0, name.length() - 6).replace("/", ".").replace("\\", ".");
                }
                return null;
            });
            classNameSet.addAll(classList);
        }

        // 继续查找 @Import(Xyz.class) 导入的 Class 配置
        Import importConfig = configClass.getAnnotation(Import.class);
        if (importConfig != null) {
            for (Class<?> importConfigClass : importConfig.value()) {
                String importClassName = importConfigClass.getName();
                classNameSet.add(importClassName);
            }
        }
        return classNameSet;
    }

    /**
     * 根据候选类名集合创建 BeanDefinition 映射。
     *
     * @param classNameSet 候选类名集合
     * @return 以 beanName 为 key 的 BeanDefinition 映射
     */
    Map<String, BeanDefinition> createBeanDefinitions(Set<String> classNameSet) {
        Map<String, BeanDefinition> beanDefs = new HashMap<>();
        for (String className : classNameSet) {
            Class<?> clazz;
            try {
                clazz = Class.forName(className);
            } catch (ClassNotFoundException e) {
                throw new BeanCreationException(e);
            }
            if (clazz.isAnnotation() || clazz.isEnum() ||
                clazz.isInterface() || clazz.isRecord()) {
                continue;
            }
            Component component = AnnotationUtils.findAnnotation(clazz, Component.class);
            if (component != null) {
                String beanName = AnnotationUtils.getBeanName(clazz);
                var BeanDef = new BeanDefinition(
                    beanName, clazz, getSuitableConstructor(clazz),
                    getOrder(clazz), clazz.isAnnotationPresent(Primary.class),
                    // init/destroy 方法名
                    null, null,
                    // 查找 @PostConstruct 方法
                    AnnotationUtils.findAnnotationMethod(clazz, PostConstruct.class),
                    // 查找 @PreDestroy 方法
                    AnnotationUtils.findAnnotationMethod(clazz, PreDestroy.class)
                );
                addBeanDefinitions(beanDefs, BeanDef);
                // 查找是否有 @Configuration，视作 Bean 的工厂
                Configuration configuration = AnnotationUtils.findAnnotation(clazz, Configuration.class);
                if (configuration != null) {
                    scanFactoryMethods(beanName, clazz, beanDefs);
                }
            }
        }
        return beanDefs;
    }

    /**
     * 按类型查找所有匹配的 BeanDefinition，并按顺序排序返回。
     *
     * @param type 目标类型
     * @return 匹配的 BeanDefinition 列表（可能为空）
     */
    List<BeanDefinition> findBeanDefinitions(Class<?> type) {
        return this.beans.values().stream()
            // 按类型过滤
            .filter(def -> type.isAssignableFrom(def.getBeanClass()))
            // 排序
            .sorted().collect(Collectors.toList());
    }

    /**
     * 按名称查找 BeanDefinition。
     *
     * @param name Bean 标识名
     * @return 对应 BeanDefinition，不存在则返回 {@code null}
     */
    @Nullable
    public BeanDefinition findBeanDefinition(String name) {
        return this.beans.get(name);
    }

    /**
     * 根据 name 查找 BeanDefinition，使用 requiredType 进行类型校验。
     * @param name Bean 标识名
     * @param requiredType Bean 的类型，用作校验
     * @return 对应的 BeanDefinition，若 name 不存在则返回 null
     * @throws BeanNotOfRequiredTypeException 如果 name 存在但对应的 bean 与 requiredType 不匹配，则抛出 BeanNotOfRequiredTypeException
     */
    @Nullable
    public BeanDefinition findBeanDefinition(String name, Class<?> requiredType) {
        BeanDefinition def = findBeanDefinition(name);
        if (def == null) {
            return null;
        }
        if (!requiredType.isAssignableFrom(def.getBeanClass())) {
            throw new BeanNotOfRequiredTypeException(String.format("Autowire required type '%s' but bean '%s' has actual type '%s'.", requiredType.getName(),
                name, def.getBeanClass().getName()));
        }
        return def;
    }

    /**
     * 根据 type 来查找某个 BeanDefinition
     *
     * @param type 指定的 type
     * @return 若不存在返回 null，如果存在多个返回 @Primary 注解的那个
     * @throws NoUniqueBeanDefinitionException 若存在多个 BeanDefinition 且没有 @Primary 注解
     */
    @Nullable
    public BeanDefinition findBeanDefinition(Class<?> type) {
        List<BeanDefinition> beanDefs = findBeanDefinitions(type);
        if (beanDefs.isEmpty()) {
            return null;
        }
        if (beanDefs.size() == 1) {
            return beanDefs.getFirst();
        }
        List<BeanDefinition> primaryDefs = beanDefs.stream().filter(BeanDefinition::isPrimary).toList();
        if (primaryDefs.size() == 1) {
            return primaryDefs.getFirst();
        }
        if (primaryDefs.isEmpty()) {
            throw new NoUniqueBeanDefinitionException(
                String.format("Multiple bean with type '%s' found, but no @Primary specified", type)
            );
        } else {
            throw new NoUniqueBeanDefinitionException(
                String.format("Multiple bean whit type '%s' found, and multiple @Primary specified", type)
            );
        }
    }

    /**
     * 选择用于实例化 Bean 的构造方法。
     *
     * @param clazz Bean 类型
     * @return 可用构造方法（且唯一）
     * @throws BeanDefinitionException 当存在多个构造方法无法确定时抛出
     */
    Constructor<?> getSuitableConstructor(Class<?> clazz) {
        Constructor<?>[] constructors = clazz.getConstructors();
        if (constructors.length == 0) {
            constructors = clazz.getDeclaredConstructors();
            if (constructors.length != 1) {
                throw new BeanDefinitionException(
                    "More than one constructor found in class :" + clazz.getName()
                );
            }
        }
        if (constructors.length != 1) {
            throw new BeanDefinitionException(
                "More than one public constructor found in class :" + clazz.getName()
            );
        }
        return constructors[0];
    }

    /**
     * 扫描配置类中的 {@code @Bean} 工厂方法并注册为 BeanDefinition。
     *
     * @param factoryBeanName 工厂 Bean 名称
     * @param clazz 配置类类型
     * @param beanDefs 已收集的 BeanDefinition 映射
     */
    void scanFactoryMethods(String factoryBeanName, Class<?> clazz, Map<String, BeanDefinition> beanDefs) {
        for (Method method : clazz.getDeclaredMethods()) {
            Bean bean = method.getAnnotation(Bean.class);
            if (bean != null) {
                checkModifiers(clazz, method);
                checkAClass(clazz, method);
                Class<?> beanClass = method.getReturnType();
                var beanDef = new BeanDefinition(AnnotationUtils.getBeanName(method), beanClass,
                    factoryBeanName, method, getOrder(method),
                    method.isAnnotationPresent(Primary.class),
                    // init method:
                    bean.initMethod().isEmpty() ? null : bean.initMethod(),
                    // destroy method:
                    bean.destroyMethod().isEmpty() ? null : bean.destroyMethod(),
                    // @PostConstruct / @PreDestroy method:
                    null, null);
                addBeanDefinitions(beanDefs, beanDef);
                logger.atDebug().log("define bean: {}", beanDef);
            }
        }
    }

    /**
     * 校验 {@code @Bean} 方法修饰符是否合法。
     *
     * @param clazz 声明该方法的类
     * @param method 待校验方法
     * @throws BeanDefinitionException 当方法为 abstract/final/private 时抛出
     */
    void checkModifiers(Class<?> clazz, Method method) {
        int modifiers = method.getModifiers();
        if (Modifier.isAbstract(modifiers)) {
            throw new BeanDefinitionException(
                "@Bean method " + clazz.getName() + "." +
                    method.getName() + " must not be abstract."
            );
        }
        if (Modifier.isFinal(modifiers)) {
            throw new BeanDefinitionException(
                "@Bean method " + clazz.getName() + "." +
                    method.getName() + " must not be final."
            );
        }
        if (Modifier.isPrivate(modifiers)) {
            throw new BeanDefinitionException(
                "@Bean method " + clazz.getName() + "." +
                    method.getName() + " must not be private."
            );
        }
    }

    /**
     * 校验 {@code @Bean} 方法返回类型是否合法。
     *
     * @param clazz 声明该方法的类
     * @param method 待校验方法
     * @throws BeanDefinitionException 当返回 primitive 或 void 时抛出
     */
    void checkAClass(Class<?> clazz, Method method) {
        Class<?> beanClass = method.getReturnType();
        if (beanClass.isPrimitive()) {
            throw new BeanDefinitionException(
                "@Bean method " + clazz.getName() + "." +
                    method.getName() + " must not return primitive type."
            );
        }
        if (beanClass == void.class || beanClass == Void.class) {
            throw new BeanDefinitionException(
                "@Bean method " + clazz.getName() + "." +
                    method.getName() + " must not return void."
            );
        }
    }

    /**
     * 注册单个 BeanDefinition，并校验名称不重复。
     *
     * @param beanDefs 目标 BeanDefinition 映射
     * @param beanDef 待注册定义
     * @throws BeanDefinitionException 当 beanName 重复时抛出
     */
    void addBeanDefinitions(Map<String, BeanDefinition> beanDefs, BeanDefinition beanDef) {
        if (beanDefs.put(beanDef.getName(), beanDef) != null) {
            throw new BeanDefinitionException(
                "Duplicate bean name:" + beanDef.getName()
            );
        }
    }

    /**
     * 获取类级别的排序值。
     *
     * @param clazz 目标类
     * @return {@code @Order} 值；未标注时返回 {@link Integer#MAX_VALUE}
     */
    int getOrder(Class<?> clazz) {
        Order order = clazz.getAnnotation(Order.class);
        return order == null ? Integer.MAX_VALUE : order.value();
    }

    /**
     * 获取 {@code @Bean} 方法级别的排序值。
     *
     * @param method 目标方法
     * @return {@code @Order} 值；未标注时返回 {@link Integer#MAX_VALUE}
     */
    int getOrder(Method method) {
        Order order = method.getAnnotation(Order.class);
        return order == null ? Integer.MAX_VALUE : order.value();
    }

    public PropertyResolver getPropertyResolver() {
        return propertyResolver;
    }
}
