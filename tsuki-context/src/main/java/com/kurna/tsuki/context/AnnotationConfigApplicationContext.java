package com.kurna.tsuki.context;

import com.kurna.tsuki.annotation.Autowired;
import com.kurna.tsuki.annotation.Bean;
import com.kurna.tsuki.annotation.Component;
import com.kurna.tsuki.annotation.ComponentScan;
import com.kurna.tsuki.annotation.Configuration;
import com.kurna.tsuki.annotation.Import;
import com.kurna.tsuki.annotation.Order;
import com.kurna.tsuki.annotation.Primary;
import com.kurna.tsuki.annotation.Value;
import com.kurna.tsuki.exception.BeanCreationException;
import com.kurna.tsuki.exception.BeanDefinitionException;
import com.kurna.tsuki.exception.BeanNotOfRequiredTypeException;
import com.kurna.tsuki.exception.InjectionException;
import com.kurna.tsuki.exception.NoSuchBeanDefinitionException;
import com.kurna.tsuki.exception.NoUniqueBeanDefinitionException;
import com.kurna.tsuki.exception.ResourceScanException;
import com.kurna.tsuki.exception.UnsatisfiedDependencyException;
import com.kurna.tsuki.io.PropertyResolver;
import com.kurna.tsuki.io.ResourceResolver;
import com.kurna.tsuki.utils.AnnotationUtils;
import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
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

    protected final Map<String, BeanDefinition> beanDefs;

    protected final PropertyResolver propertyResolver;

    // 当前正在创建的所有 Bean 的名称
    private final Set<String> creatingBeanNames;

    /**
     * 根据配置类创建应用上下文并完成 BeanDefinition 扫描与解析。
     *
     * @param configClass      配置类（用于确定扫描包与 {@code @Import} 入口）
     * @param propertyResolver 属性解析器
     * @throws ResourceScanException 当包资源扫描失败时抛出
     */
    public AnnotationConfigApplicationContext(Class<?> configClass, PropertyResolver propertyResolver)
        throws ResourceScanException {
        this.propertyResolver = propertyResolver;
        // 扫描获取所有的 Bean 的 Class
        final Set<String> beanClassNames = scanForBeanClassNames(configClass);

        // 创建 Bean 的定义
        this.beanDefs = createBeanDefinitions(beanClassNames);

        // 用于检测循环依赖，若重复则出现循环依赖
        this.creatingBeanNames = new HashSet<>();

        // 创建 @Configuration 类型的 Bean 实例（强依赖）
        this.beanDefs.values().stream()
            .filter(beanDef ->
                AnnotationUtils.findAnnotation(beanDef.getBeanClass(), Configuration.class) != null)
            .forEach(this::createBeanAsEarlySingleton);

        // 创建其他普通 Bean
        createNormalBeans();

        // 通过字段和set方法注入依赖:
        this.beanDefs.values().forEach(this::injectBean);

        // 调用init方法:
        this.beanDefs.values().forEach(this::initBean);

        if (logger.isDebugEnabled()) {
            this.beanDefs.values().stream().sorted().forEach(beanDef -> logger.debug("bean initialized: {}", beanDef));
        }
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
                var beanDef = new BeanDefinition(
                    beanName, clazz, getSuitableConstructor(clazz),
                    getOrder(clazz), clazz.isAnnotationPresent(Primary.class),
                    // init/destroy 方法名
                    null, null,
                    // 查找 @PostConstruct 方法
                    AnnotationUtils.findAnnotationMethod(clazz, PostConstruct.class),
                    // 查找 @PreDestroy 方法
                    AnnotationUtils.findAnnotationMethod(clazz, PreDestroy.class)
                );
                addBeanDefinitions(beanDefs, beanDef);
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
     * @param clazz           配置类类型
     * @param beanDefs        已收集的 BeanDefinition 映射
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
     * @param clazz  声明该方法的类
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
     * @param clazz  声明该方法的类
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
     * @param beanDef  待注册定义
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
     * 创建普通 Bean（非 {@code @Configuration} 工厂 Bean）。
     * <p>
     * 按 {@link BeanDefinition#compareTo(BeanDefinition)} 排序后逐个初始化，
     * 已在依赖解析阶段提前创建的 Bean 会被跳过。
     * </p>
     */
    void createNormalBeans() {
        // 获取 BeanDefinition 列表
        List<BeanDefinition> beanDefs = this.beanDefs.values().stream()
            .filter(beanDef -> beanDef.getInstance() == null)
            .sorted().toList();

        beanDefs.forEach(beanDef -> {
            // 如果 Bean 未被创建（可能在其他 Bean 的构造方法注入前被创建）
            if (beanDef.getInstance() == null) {
                // 创建 Bean
                createBeanAsEarlySingleton(beanDef);
            }
        });
    }

    /**
     * 以“早期单例”方式创建指定 Bean。
     * <p>
     * 该过程会完成以下步骤：
     * <ul>
     *   <li>检测循环依赖（基于当前创建链路中的 beanName）；</li>
     *   <li>解析构造方法或 {@code @Bean} 工厂方法参数（{@code @Value}/{@code @Autowired}）；</li>
     *   <li>递归创建尚未初始化的依赖 Bean；</li>
     *   <li>实例化并回填到 {@link BeanDefinition}。</li>
     * </ul>
     * </p>
     *
     * @param beanDef 待创建的 BeanDefinition
     * @return 创建完成后的 Bean 实例
     * @throws UnsatisfiedDependencyException 当检测到循环依赖时抛出
     * @throws BeanCreationException          当缺少可用创建入口、参数无法解析或实例化失败时抛出
     */
    Object createBeanAsEarlySingleton(BeanDefinition beanDef) {
        logger.atDebug().log("Try create bean '{}' as early singleton: {}", beanDef.getName(), beanDef.getBeanClass().getName());
        if (!this.creatingBeanNames.add(beanDef.getName())) {
            // 重复创建 Bean 导致的循环依赖
            throw new UnsatisfiedDependencyException(
                String.format("Circular dependency detected when creating bean '%s' of type '%s'.", beanDef.getName(), beanDef.getBeanClass().getName())
            );
        }
        // 创建方法：构造方法或工厂方法
        Executable createFn = beanDef.getFactoryMethodName() == null ?
            beanDef.getConstructor() : beanDef.getFactoryMethod();
        if (createFn == null) {
            throw new BeanCreationException(
                String.format("No suitable create function found for bean '%s' of type '%s'.", beanDef.getName(), beanDef.getBeanClass().getName())
            );
        }

        // 创建参数
        Parameter[] params = createFn.getParameters();
        Annotation[][] paramsAnnotations = createFn.getParameterAnnotations();
        Object[] args = new Object[params.length];
        for (int i = 0; i < params.length; i++) {
            Parameter param = params[i];
            Annotation[] paramAnnotations = paramsAnnotations[i];
            Value value = AnnotationUtils.getAnnotation(paramAnnotations, Value.class);
            Autowired autowired = AnnotationUtils.getAnnotation(paramAnnotations, Autowired.class);
            // @Configuration 类型的 Bean 是工厂，不允许使用 @Autowired 创建
            final boolean isConfiguration =
                AnnotationUtils.findAnnotation(beanDef.getBeanClass(), Configuration.class) != null;
            if (isConfiguration && autowired != null) {
                throw new BeanCreationException(
                    String.format("Cannot specify @Autowired when create @Configuration bean '%s': %s.", beanDef.getName(), beanDef.getBeanClass().getName())
                );
            }
            if (value == null && autowired == null) {
                throw new BeanCreationException(
                    String.format("Must specify @Autowired or @Value when create bean '%s': %s.", beanDef.getName(), beanDef.getBeanClass().getName())
                );
            }
            Class<?> paramType = param.getType();
            if (value != null) {
                // 参数是 @Value
                args[i] = this.propertyResolver.getProperty(value.value(), paramType);
            } else {
                // 参数是 @Autowired
                String name = autowired.name();
                boolean required = autowired.value();
                BeanDefinition dependsOnDef = name.isEmpty() ?
                    findBeanDefinition(paramType) : findBeanDefinition(name, paramType);
                if (required && dependsOnDef == null) {
                    throw new BeanCreationException(
                        String.format("Missing autowired bean with type " +
                                "'%s' when create bean '%s': %s.", paramType.getName(),
                            beanDef.getName(), beanDef.getBeanClass().getName())
                    );
                }
                if (dependsOnDef != null) {
                    // 获取依赖的 Bean
                    Object autowiredBeanInstance = dependsOnDef.getInstance();
                    if (autowiredBeanInstance == null) {
                        // 当前依赖 Bean 尚未初始化，递归调用初始化该依赖 Bean
                        autowiredBeanInstance = createBeanAsEarlySingleton(dependsOnDef);
                    }
                    args[i] = autowiredBeanInstance;
                } else {
                    args[i] = null;
                }
            }
        }

        // 创建 Bean 实例
        Object instance = null;
        if (beanDef.getFactoryMethodName() == null) {
            // 用构造方法创建
            try {
                if (beanDef.getConstructor() != null) {
                    instance = beanDef.getConstructor().newInstance(args);
                }
            } catch (Exception e) {
                throw new BeanCreationException(
                    String.format("Exception when create bean '%s': %s.", beanDef.getName(), beanDef.getBeanClass().getName())
                );
            }
        } else {
            // 用 @Bean 工厂方法创建
            Object configInstance = getBean(beanDef.getFactoryMethodName());
            try {
                if (beanDef.getFactoryMethod() != null) {
                    instance = beanDef.getFactoryMethod().invoke(configInstance, args);
                }
            } catch (Exception e) {
                throw new BeanCreationException(
                    String.format("Exception when create bean '%s': %s.", beanDef.getName(), beanDef.getBeanClass().getName())
                );
            }
        }
        beanDef.setInstance(instance);
        return beanDef.getInstance();
    }

    /**
     * 注入依赖但不调用init方法
     */
    void injectBean(BeanDefinition def) {
        try {
            injectProperties(def, def.getBeanClass(), def.getInstance());
        } catch (ReflectiveOperationException e) {
            throw new BeanCreationException(e);
        }
    }

    /**
     * 调用init方法
     */
    void initBean(BeanDefinition def) {
        // 调用init方法:
        callMethod(def.getInstance(), def.getInitMethod(), def.getInitMethodName());
    }

    void callMethod(Object beanInstance, Method method, String namedMethod) {
        // 调用init/destroy方法
        if (method != null) {
            try {
                method.invoke(beanInstance);
            } catch (ReflectiveOperationException e) {
                throw new BeanCreationException(e);
            }
        } else if (namedMethod != null) {
            // 查找initMethod/destroyMethod="xyz"，注意是在实际类型中查找
            Method named = AnnotationUtils.getNamedMethod(beanInstance.getClass(), namedMethod);
            named.setAccessible(true);
            try {
                named.invoke(beanInstance);
            } catch (ReflectiveOperationException e) {
                throw new BeanCreationException(e);
            }
        }
    }

    /**
     * 注入属性
     */
    void injectProperties(BeanDefinition def, Class<?> clazz, Object bean) throws ReflectiveOperationException {
        // 在当前类查找Field和Method并注入
        for (Field f : clazz.getDeclaredFields()) {
            tryInjectProperties(def, clazz, bean, f);
        }
        for (Method m : clazz.getDeclaredMethods()) {
            tryInjectProperties(def, clazz, bean, m);
        }
        // 在父类查找Field和Method并注入（防止遗漏父类的依赖注入）
        Class<?> superClazz = clazz.getSuperclass();
        if (superClazz != null) {
            injectProperties(def, superClazz, bean);
        }
    }

    /**
     * 注入单个属性
     */
    void tryInjectProperties(BeanDefinition def, Class<?> clazz, Object bean, AccessibleObject acc) throws ReflectiveOperationException {
        Value value = acc.getAnnotation(Value.class);
        Autowired autowired = acc.getAnnotation(Autowired.class);
        if (value == null && autowired == null) {
            return;
        }

        Field field = null;
        Method method = null;
        if (acc instanceof Field f) {
            checkFieldOrMethod(f);
            f.setAccessible(true);
            field = f;
        } else if (acc instanceof Method m) {
            checkFieldOrMethod(m);
            if (m.getParameters().length != 1) {
                throw new BeanDefinitionException(
                    String.format("Cannot inject a non-setter method %s for bean '%s': %s", m.getName(), def.getName(), def.getBeanClass().getName()));
            }
            m.setAccessible(true);
            method = m;
        } else {
            throw new InjectionException(
                String.format("Unsupported injection target: %s for bean '%s': %s", acc, def.getName(), def.getBeanClass().getName())
            );
        }

        String accessibleName = field != null ? field.getName() : method.getName();
        Class<?> accessibleType = field != null ? field.getType() : method.getParameterTypes()[0];

        if (value != null && autowired != null) {
            throw new BeanCreationException(String.format("Cannot specify both @Autowired and @Value when inject %s.%s for bean '%s': %s",
                clazz.getSimpleName(), accessibleName, def.getName(), def.getBeanClass().getName()));
        }

        // @Value注入:
        if (value != null) {
            Object propValue = this.propertyResolver.getProperty(value.value(), accessibleType);
            // 字段：注入值
            if (field != null) {
                logger.atDebug().log("Field value injection: {}.{} = {}", def.getBeanClass().getName(), accessibleName, propValue);
                field.set(bean, propValue);
            }
            // 方法：传入参数
            if (method != null) {
                logger.atDebug().log("Method value injection: {}.{} ({})", def.getBeanClass().getName(), accessibleName, propValue);
                method.invoke(bean, propValue);
            }
        }

        // @Autowired注入:
        if (autowired != null) {
            String name = autowired.name();
            boolean required = autowired.value();
            Object depends = name.isEmpty() ? findBean(accessibleType) : findBean(name, accessibleType);
            if (required && depends == null) {
                throw new UnsatisfiedDependencyException(
                    String.format("Dependency bean not found when inject %s.%s for bean '%s': %s",
                        clazz.getSimpleName(), accessibleName, def.getName(), def.getBeanClass().getName())
                );
            }
            if (depends != null) {
                if (field != null) {
                    logger.atDebug().log("Field autowired injection: {}.{} = {}", def.getBeanClass().getName(), accessibleName, depends);
                    field.set(bean, depends);
                }
                if (method != null) {
                    logger.atDebug().log("Method autowired injection: {}.{} ({})", def.getBeanClass().getName(), accessibleName, depends);
                    method.invoke(bean, depends);
                }
            }
        }
    }

    void checkFieldOrMethod(Member m) {
        int mod = m.getModifiers();
        if (Modifier.isStatic(mod)) {
            throw new BeanDefinitionException("Cannot inject static field: " + m);
        }
        if (Modifier.isFinal(mod)) {
            if (m instanceof Field field) {
                throw new BeanDefinitionException("Cannot inject final field: " + field);
            }
            if (m instanceof Method) {
                logger.warn(
                    "Inject final method should be careful because it is not called on target bean when bean is proxied and may cause NullPointerException.");
            }
        }
    }

    /**
     * 按类型查找所有匹配的 BeanDefinition，并按顺序排序返回。
     *
     * @param type 目标类型
     * @return 匹配的 BeanDefinition 列表（可能为空）
     */
    List<BeanDefinition> findBeanDefinitions(Class<?> type) {
        return this.beanDefs.values().stream()
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
    BeanDefinition findBeanDefinition(String name) {
        return this.beanDefs.get(name);
    }

    /**
     * 按名称查找 BeanDefinition，并校验其是否可赋值给指定类型。
     *
     * @param name         Bean 标识名
     * @param requiredType 期望类型
     * @return 对应 BeanDefinition；若名称不存在则返回 {@code null}
     * @throws BeanNotOfRequiredTypeException 当名称存在但类型不匹配时抛出
     */
    @Nullable
    BeanDefinition findBeanDefinition(String name, Class<?> requiredType) {
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
     * 按类型查找单个 BeanDefinition。
     *
     * @param type 目标类型
     * @return 匹配的 BeanDefinition；若不存在返回 {@code null}；若存在多个则返回唯一 {@code @Primary}
     * @throws NoUniqueBeanDefinitionException 当存在多个候选且无法确定唯一 Bean 时抛出
     */
    @Nullable
    BeanDefinition findBeanDefinition(Class<?> type) {
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

    /**
     * 按名称获取 Bean。
     *
     * @param name Bean 名称
     * @param <T>  返回值泛型
     * @return 对应名称的 Bean 实例
     * @throws NoSuchBeanDefinitionException 当 name 不存在时抛出
     */
    @SuppressWarnings("unchecked")
    public <T> T getBean(String name) {
        BeanDefinition def = this.beanDefs.get(name);
        if (def == null) {
            throw new NoSuchBeanDefinitionException(String.format("No bean defined with name '%s'.", name));
        }
        return (T) def.getRequiredInstance();
    }

    /**
     * 按名称与类型获取 Bean。
     *
     * @param name         Bean 名称
     * @param requiredType 期望类型
     * @param <T>          返回值泛型
     * @return 匹配名称与类型的 Bean 实例
     * @throws NoSuchBeanDefinitionException  当 name 不存在时抛出
     * @throws BeanNotOfRequiredTypeException 当 name 存在但类型不匹配时抛出
     */
    public <T> T getBean(String name, Class<T> requiredType) {
        T t = findBean(name, requiredType);
        if (t == null) {
            throw new NoSuchBeanDefinitionException(String.format("No bean defined with name '%s' and type '%s'.", name, requiredType));
        }
        return t;
    }

    /**
     * 按类型获取所有 Bean。
     *
     * @param requiredType 目标类型
     * @param <T>          返回值泛型
     * @return 所有匹配 Bean 的实例列表；若不存在则返回空列表
     */
    @SuppressWarnings("unchecked")
    public <T> List<T> getBeans(Class<T> requiredType) {
        List<BeanDefinition> defs = findBeanDefinitions(requiredType);
        if (defs.isEmpty()) {
            return List.of();
        }
        List<T> list = new ArrayList<>(defs.size());
        for (var def : defs) {
            list.add((T) def.getRequiredInstance());
        }
        return list;
    }

    /**
     * 按类型获取单个 Bean。
     *
     * @param requiredType 目标类型
     * @param <T>          返回值泛型
     * @return 匹配类型的唯一 Bean（或 {@code @Primary} Bean）
     * @throws NoSuchBeanDefinitionException   当不存在匹配类型的 Bean 时抛出
     * @throws NoUniqueBeanDefinitionException 当存在多个候选且无法确定唯一 Bean 时抛出
     */
    @SuppressWarnings("unchecked")
    public <T> T getBean(Class<T> requiredType) {
        BeanDefinition def = findBeanDefinition(requiredType);
        if (def == null) {
            throw new NoSuchBeanDefinitionException(String.format("No bean defined with type '%s'.", requiredType));
        }
        return (T) def.getRequiredInstance();
    }

    /**
     * 判断容器中是否存在指定名称的 Bean。
     *
     * @param name Bean 名称
     * @return 存在返回 {@code true}，否则返回 {@code false}
     */
    public boolean containsBean(String name) {
        return this.beanDefs.containsKey(name);
    }

    /**
     * 按名称与类型查找 Bean；未找到时返回 {@code null}。
     *
     * @param name         Bean 名称
     * @param requiredType 期望类型
     * @param <T>          返回值泛型
     * @return 匹配实例；若不存在返回 {@code null}
     * @throws BeanNotOfRequiredTypeException 当名称存在但类型不匹配时抛出
     */
    @Nullable
    @SuppressWarnings("unchecked")
    protected <T> T findBean(String name, Class<T> requiredType) {
        BeanDefinition def = findBeanDefinition(name, requiredType);
        if (def == null) {
            return null;
        }
        return (T) def.getRequiredInstance();
    }

    /**
     * 按类型查找单个 Bean；未找到时返回 {@code null}。
     *
     * @param requiredType 目标类型
     * @param <T>          返回值泛型
     * @return 匹配实例；若不存在返回 {@code null}
     * @throws NoUniqueBeanDefinitionException 当存在多个候选且无法确定唯一 Bean 时抛出
     */
    @Nullable
    @SuppressWarnings("unchecked")
    protected <T> T findBean(Class<T> requiredType) {
        BeanDefinition def = findBeanDefinition(requiredType);
        if (def == null) {
            return null;
        }
        return (T) def.getRequiredInstance();
    }

    /**
     * 按类型查找所有 Bean；未找到时返回空列表。
     *
     * @param requiredType 目标类型
     * @param <T>          返回值泛型
     * @return 匹配实例列表
     */
    @Nullable
    @SuppressWarnings("unchecked")
    protected <T> List<T> findBeans(Class<T> requiredType) {
        return findBeanDefinitions(requiredType).stream().map(def -> (T) def.getRequiredInstance()).collect(Collectors.toList());
    }

}
