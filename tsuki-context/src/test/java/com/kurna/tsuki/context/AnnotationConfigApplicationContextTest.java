package com.kurna.tsuki.context;

import com.kurna.imported.LocalDateConfiguration;
import com.kurna.imported.ZonedDateConfiguration;
import com.kurna.scan.ScanApplication;
import com.kurna.scan.convert.ValueConverterBean;
import com.kurna.scan.custom.annotation.CustomAnnotationBean;
import com.kurna.scan.init.AnnotationInitBean;
import com.kurna.scan.init.SpecifyInitBean;
import com.kurna.scan.nested.OuterBean;
import com.kurna.scan.nested.OuterBean.NestedBean;
import com.kurna.scan.primary.DogBean;
import com.kurna.scan.primary.PersonBean;
import com.kurna.scan.primary.StudentBean;
import com.kurna.scan.primary.TeacherBean;
import com.kurna.scan.proxy.InjectProxyOnConstructorBean;
import com.kurna.scan.proxy.InjectProxyOnPropertyBean;
import com.kurna.scan.proxy.OriginBean;
import com.kurna.scan.proxy.SecondProxyBean;
import com.kurna.scan.sub1.Sub1Bean;
import com.kurna.scan.sub1.sub2.Sub2Bean;
import com.kurna.scan.sub1.sub2.sub3.Sub3Bean;
import com.kurna.tsuki.annotation.Bean;
import com.kurna.tsuki.annotation.Component;
import com.kurna.tsuki.annotation.ComponentScan;
import com.kurna.tsuki.annotation.Order;
import com.kurna.tsuki.annotation.Primary;
import com.kurna.tsuki.exception.BeanDefinitionException;
import com.kurna.tsuki.exception.BeanNotOfRequiredTypeException;
import com.kurna.tsuki.exception.NoUniqueBeanDefinitionException;
import com.kurna.tsuki.exception.ResourceScanException;
import com.kurna.tsuki.io.PropertyResolver;
import com.kurna.scan.earlysingleton.EarlySingletonConfiguration;
import com.kurna.scan.earlysingleton.EarlySingletonConsumerBean;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AnnotationConfigApplicationContextTest {

    // 测试 BeanPostProcessor
    @Test
    public void testProxy() throws ResourceScanException {
        var ctx = new AnnotationConfigApplicationContext(ScanApplication.class, createPropertyResolver());
        // test proxy:
        OriginBean proxy = ctx.getBean(OriginBean.class);
        assertSame(SecondProxyBean.class, proxy.getClass());
        assertEquals("Scan App", proxy.getName());
        assertEquals("v1.0", proxy.getVersion());
        // make sure proxy.field is not injected:
        assertNull(proxy.name);
        assertNull(proxy.version);

        // other beans are injected proxy instance:
        var inject1 = ctx.getBean(InjectProxyOnPropertyBean.class);
        var inject2 = ctx.getBean(InjectProxyOnConstructorBean.class);
        assertSame(proxy, inject1.injected);
        assertSame(proxy, inject2.injected);
    }

    // 测试 BeanDefinition 的创建和属性
    @Test
    public void testBeanDefinition() throws ResourceScanException, NoSuchMethodException {
        var ctx = createContext(ScanApplication.class);

        BeanDefinition def = new BeanDefinition(
            "testBean",
            TestBean.class,
            ctx.getSuitableConstructor(TestBean.class),
            5,
            true,
            "init",
            "destroy",
            null,
            null
        );

        assertEquals("testBean", def.name);
        assertEquals(TestBean.class, def.beanClass);
        assertEquals(TestBean.class.getConstructor(), def.constructor);
        assertEquals(5, def.order);
        assertTrue(def.isPrimary);
        assertEquals("init", def.initMethodName);
        assertEquals("destroy", def.destroyMethodName);
        assertNull(def.initMethod);
        assertNull(def.destroyMethod);

        // @CustomAnnotation
        assertNotNull(ctx.findBeanDefinition(CustomAnnotationBean.class));
        assertNotNull(ctx.findBeanDefinition("customAnnotation"));

        // @Import()
        assertNotNull(ctx.findBeanDefinition(LocalDateConfiguration.class));
        assertNotNull(ctx.findBeanDefinition("startLocalDate"));
        assertNotNull(ctx.findBeanDefinition("startLocalDateTime"));
        assertNotNull(ctx.findBeanDefinition(ZonedDateConfiguration.class));
        assertNotNull(ctx.findBeanDefinition("startZonedDateTime"));

        // nested
        assertNotNull(ctx.findBeanDefinition(OuterBean.class));
        assertNotNull(ctx.findBeanDefinition(NestedBean.class));

        BeanDefinition studentDef = ctx.findBeanDefinition(StudentBean.class);
        BeanDefinition teacherDef = ctx.findBeanDefinition(TeacherBean.class);
        // 2 PersonBean
        List<BeanDefinition> defs = ctx.findBeanDefinitions(PersonBean.class);
        assertSame(studentDef, defs.get(0));
        assertSame(teacherDef, defs.get(1));
        // 1 @Primary PersonBean
        BeanDefinition personPrimaryDef = ctx.findBeanDefinition(PersonBean.class);
        assertSame(teacherDef, personPrimaryDef);
    }

    // 测试创建 Bean 实例
    @Test
    public void testBeanCreation() throws ResourceScanException {
        var ctx = new AnnotationConfigApplicationContext(ScanApplication.class, createPropertyResolver());
        // testCustomAnnotation
        assertNotNull(ctx.getBean(CustomAnnotationBean.class));
        assertNotNull(ctx.getBean("customAnnotation"));
        // testImport
        assertNotNull(ctx.getBean(LocalDateConfiguration.class));
        assertNotNull(ctx.getBean("startLocalDate"));
        assertNotNull(ctx.getBean("startLocalDateTime"));
        assertNotNull(ctx.getBean(ZonedDateConfiguration.class));
        assertNotNull(ctx.getBean("startZonedDateTime"));
        // testNested
        ctx.getBean(OuterBean.class);
        ctx.getBean(NestedBean.class);
        // testPrimary
        var person = ctx.getBean(PersonBean.class);
        assertEquals(TeacherBean.class, person.getClass());
        var dog = ctx.getBean(DogBean.class);
        assertEquals("Husky", dog.type);
        // testSub
        ctx.getBean(Sub1Bean.class);
        ctx.getBean(Sub2Bean.class);
        ctx.getBean(Sub3Bean.class);
    }

    // 测试初始化 bean
    @Test
    public void testInitMethod() throws ResourceScanException {
        var ctx = new AnnotationConfigApplicationContext(ScanApplication.class, createPropertyResolver());
        // test @PostConstruct:
        var bean1 = ctx.getBean(AnnotationInitBean.class);
        var bean2 = ctx.getBean(SpecifyInitBean.class);
        assertEquals("Default App Title / v1.0", bean1.appName);
        assertEquals("Scan App / v1.0", bean2.appName);
        assertEquals("Scan App / v1.0", bean1.specifyInitBean.appName);
    }

    @Test
    public void testConverter() throws ResourceScanException {
        var ctx = new AnnotationConfigApplicationContext(ScanApplication.class, createPropertyResolver());
        var bean = ctx.getBean(ValueConverterBean.class);

        assertNotNull(bean.injectedBoolean);
        assertTrue(bean.injectedBooleanPrimitive);
        assertTrue(bean.injectedBoolean);

        assertNotNull(bean.injectedByte);
        assertEquals((byte) 123, bean.injectedByte);
        assertEquals((byte) 123, bean.injectedBytePrimitive);

        assertNotNull(bean.injectedShort);
        assertEquals((short) 12345, bean.injectedShort);
        assertEquals((short) 12345, bean.injectedShortPrimitive);

        assertNotNull(bean.injectedInteger);
        assertEquals(1234567, bean.injectedInteger);
        assertEquals(1234567, bean.injectedIntPrimitive);

        assertNotNull(bean.injectedLong);
        assertEquals(123456789_000L, bean.injectedLong);
        assertEquals(123456789_000L, bean.injectedLongPrimitive);

        assertNotNull(bean.injectedFloat);
        assertEquals(12345.6789F, bean.injectedFloat, 0.0001F);
        assertEquals(12345.6789F, bean.injectedFloatPrimitive, 0.0001F);

        assertNotNull(bean.injectedDouble);
        assertEquals(123456789.87654321, bean.injectedDouble, 0.0000001);
        assertEquals(123456789.87654321, bean.injectedDoublePrimitive, 0.0000001);

        assertEquals(LocalDate.parse("2023-03-29"), bean.injectedLocalDate);
        assertEquals(LocalTime.parse("20:45:01"), bean.injectedLocalTime);
        assertEquals(LocalDateTime.parse("2023-03-29T20:45:01"), bean.injectedLocalDateTime);
        assertEquals(ZonedDateTime.parse("2023-03-29T20:45:01+08:00[Asia/Shanghai]"), bean.injectedZonedDateTime);
        assertEquals(Duration.parse("P2DT3H4M"), bean.injectedDuration);
        assertEquals(ZoneId.of("Asia/Shanghai"), bean.injectedZoneId);
    }


    @Test
    public void testCreateBeanAsEarlySingletonLoopResolvesValueAutowiredAndOptional() throws ResourceScanException {
        var ctx = createContext(EarlySingletonConfiguration.class);
        var consumer = ctx.getBean("aConsumerBean", EarlySingletonConsumerBean.class);
        assertEquals("Scan App", consumer.title);
        assertNotNull(consumer.dependency);
        assertEquals("dep", consumer.dependency.name);
        assertNull(consumer.missingDependency);
    }

    @Test
    public void testFindBeanDefinitionByNameAndTypeWhenNameNotExists() throws ResourceScanException {
        var ctx = createContext(ScanApplication.class);
        assertNull(ctx.findBeanDefinition("notExists", PersonBean.class));
    }

    @Test
    public void testFindBeanDefinitionByNameAndTypeWhenTypeMismatch() throws ResourceScanException {
        var ctx = createContext(ScanApplication.class);
        assertThrows(BeanNotOfRequiredTypeException.class,
            () -> ctx.findBeanDefinition("studentBean", TeacherBean.class));
    }

    @Test
    public void testFindBeanDefinitionByTypeWhenNoBeanFound() throws ResourceScanException {
        var ctx = createContext(ScanApplication.class);
        assertNull(ctx.findBeanDefinition(java.sql.Driver.class));
    }

    @Test
    public void testFindBeanDefinitionByTypeWhenNoPrimary() throws ResourceScanException {
        var ctx = createContext(LocalFixtureScanConfig.class);
        assertThrows(NoUniqueBeanDefinitionException.class, () -> ctx.findBeanDefinition(NoPrimaryType.class));
    }

    @Test
    public void testFindBeanDefinitionByTypeWhenMultiplePrimary() throws ResourceScanException {
        var ctx = createContext(LocalFixtureScanConfig.class);
        assertThrows(NoUniqueBeanDefinitionException.class, () -> ctx.findBeanDefinition(MultiPrimaryType.class));
    }

    @Test
    public void testScanForBeanClassNames() throws ResourceScanException {
        var ctx = createContext(ScanApplication.class);
        Set<String> classNames = ctx.scanForBeanClassNames(ScanApplication.class);
        assertTrue(classNames.contains(LocalDateConfiguration.class.getName()));
        assertTrue(classNames.contains(ZonedDateConfiguration.class.getName()));
        assertTrue(classNames.contains(StudentBean.class.getName()));
    }

    @Test
    public void testScanForBeanClassNamesWithExplicitPackage() throws ResourceScanException {
        var ctx = createContext(ScanApplication.class);
        Set<String> classNames = ctx.scanForBeanClassNames(PrimaryScanConfig.class);
        assertTrue(classNames.contains(StudentBean.class.getName()));
        assertTrue(classNames.contains(TeacherBean.class.getName()));
        assertFalse(classNames.contains(LocalDateConfiguration.class.getName()));
    }

    @Test
    public void testCreateBeanDefinitionsFiltersOutUnsupportedTypes() throws ResourceScanException {
        var ctx = createContext(ScanApplication.class);
        Map<String, BeanDefinition> defs = ctx.createBeanDefinitions(Set.of(
            NoPrimaryOne.class.getName(),
            FixtureAnnotation.class.getName(),
            FixtureEnum.class.getName(),
            FixtureInterface.class.getName(),
            FixtureRecord.class.getName()
        ));
        assertEquals(1, defs.size());
        assertNotNull(defs.get("noPrimaryOne"));
    }

    @Test
    public void testGetSuitableConstructor() throws ResourceScanException {
        var ctx = createContext(ScanApplication.class);
        assertNotNull(ctx.getSuitableConstructor(PrivateSingleCtorBean.class));
        assertThrows(BeanDefinitionException.class, () -> ctx.getSuitableConstructor(MultiCtorBean.class));
    }

    @Test
    public void testCheckModifiers() throws Exception {
        var ctx = createContext(ScanApplication.class);

        Method abstractMethod = AbstractFactory.class.getDeclaredMethod("abstractBean");
        assertThrows(BeanDefinitionException.class, () -> ctx.checkModifiers(AbstractFactory.class, abstractMethod));

        Method finalMethod = FinalFactory.class.getDeclaredMethod("finalBean");
        assertThrows(BeanDefinitionException.class, () -> ctx.checkModifiers(FinalFactory.class, finalMethod));

        Method privateMethod = PrivateFactory.class.getDeclaredMethod("privateBean");
        assertThrows(BeanDefinitionException.class, () -> ctx.checkModifiers(PrivateFactory.class, privateMethod));
    }

    @Test
    public void testCheckAClass() throws Exception {
        var ctx = createContext(ScanApplication.class);

        Method primitiveMethod = PrimitiveFactory.class.getDeclaredMethod("primitiveBean");
        assertThrows(BeanDefinitionException.class, () -> ctx.checkAClass(PrimitiveFactory.class, primitiveMethod));

        Method voidMethod = VoidFactory.class.getDeclaredMethod("voidBean");
        assertThrows(BeanDefinitionException.class, () -> ctx.checkAClass(VoidFactory.class, voidMethod));
    }

    @Test
    public void testAddBeanDefinitionsDuplicateName() throws ResourceScanException {
        var ctx = createContext(ScanApplication.class);
        Map<String, BeanDefinition> defs = new HashMap<>();

        BeanDefinition first = new BeanDefinition(
            "duplicateBean",
            DuplicateBean.class,
            ctx.getSuitableConstructor(DuplicateBean.class),
            Integer.MAX_VALUE,
            false,
            null,
            null,
            null,
            null
        );
        BeanDefinition second = new BeanDefinition(
            "duplicateBean",
            NoPrimaryOne.class,
            ctx.getSuitableConstructor(NoPrimaryOne.class),
            Integer.MAX_VALUE,
            false,
            null,
            null,
            null,
            null
        );

        ctx.addBeanDefinitions(defs, first);
        assertThrows(BeanDefinitionException.class, () -> ctx.addBeanDefinitions(defs, second));
    }

    @Test
    public void testGetOrder() throws Exception {
        var ctx = createContext(ScanApplication.class);
        assertEquals(3, ctx.getOrder(OrderedBean.class));
        assertEquals(Integer.MAX_VALUE, ctx.getOrder(DefaultOrderBean.class));

        Method orderedMethod = OrderedFactory.class.getDeclaredMethod("orderedBean");
        Method defaultMethod = OrderedFactory.class.getDeclaredMethod("defaultOrderedBean");
        assertEquals(7, ctx.getOrder(orderedMethod));
        assertEquals(Integer.MAX_VALUE, ctx.getOrder(defaultMethod));
    }

    AnnotationConfigApplicationContext createContext(Class<?> configClass) throws ResourceScanException {
        return new AnnotationConfigApplicationContext(configClass, createPropertyResolver());
    }

    PropertyResolver createPropertyResolver() {
        var ps = new Properties();
        ps.put("app.title", "Scan App");
        ps.put("app.version", "v1.0");
        ps.put("jdbc.url", "jdbc:hsqldb:file:testdb.tmp");
        ps.put("jdbc.username", "sa");
        ps.put("jdbc.password", "");
        ps.put("convert.boolean", "true");
        ps.put("convert.byte", "123");
        ps.put("convert.short", "12345");
        ps.put("convert.integer", "1234567");
        ps.put("convert.long", "123456789000");
        ps.put("convert.float", "12345.6789");
        ps.put("convert.double", "123456789.87654321");
        ps.put("convert.localdate", "2023-03-29");
        ps.put("convert.localtime", "20:45:01");
        ps.put("convert.localdatetime", "2023-03-29T20:45:01");
        ps.put("convert.zoneddatetime", "2023-03-29T20:45:01+08:00[Asia/Shanghai]");
        ps.put("convert.duration", "P2DT3H4M");
        ps.put("convert.zoneid", "Asia/Shanghai");
        return new PropertyResolver(ps);
    }

    @ComponentScan("com.kurna.scan.primary")
    static class PrimaryScanConfig {
    }

    @ComponentScan("com.kurna.tsuki.context")
    static class LocalFixtureScanConfig {
    }

    interface NoPrimaryType {
    }

    @Component
    static class NoPrimaryOne implements NoPrimaryType {
    }

    @Component
    static class NoPrimaryTwo implements NoPrimaryType {
    }

    interface MultiPrimaryType {
    }

    @Primary
    @Component
    static class MultiPrimaryOne implements MultiPrimaryType {
    }

    @Primary
    @Component
    static class MultiPrimaryTwo implements MultiPrimaryType {
    }

    static class TestBean {
        public TestBean() {

        }
    }

    static class DuplicateBean {
        public DuplicateBean() {
        }
    }

    static class PrivateSingleCtorBean {
        private PrivateSingleCtorBean() {
        }
    }

    static class MultiCtorBean {
        public MultiCtorBean() {
        }

        public MultiCtorBean(String name) {
        }
    }

    abstract static class AbstractFactory {
        @Bean
        abstract String abstractBean();
    }

    static class FinalFactory {
        @Bean
        final String finalBean() {
            return "ok";
        }
    }

    static class PrivateFactory {
        @Bean
        private String privateBean() {
            return "ok";
        }
    }

    static class PrimitiveFactory {
        @Bean
        int primitiveBean() {
            return 1;
        }
    }

    static class VoidFactory {
        @Bean
        void voidBean() {
        }
    }

    @Order(3)
    static class OrderedBean {
    }

    static class DefaultOrderBean {
    }

    static class OrderedFactory {
        @Order(7)
        @Bean
        String orderedBean() {
            return "ok";
        }

        @Bean
        String defaultOrderedBean() {
            return "ok";
        }
    }

    @interface FixtureAnnotation {
    }

    enum FixtureEnum {
        A
    }

    interface FixtureInterface {
    }

    record FixtureRecord(String value) {
    }
}
