package com.kurna.tsuki.io;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.sql.DataSourceDefinition;
import jakarta.annotation.sub.AnnoScan;

public class ResourceResolverTest {

    private static final String SCAN_BASE_PACKAGE = "com.kurna.scan";

    private static String mapClassResource(Resource resource) {
        String resourceName = resource.name();
        if (resourceName.endsWith(".class")) {
            return resourceName.substring(0, resourceName.length() - 6).replace('/', '.').replace('\\', '.');
        }
        return null;
    }

    private static String mapTextResource(Resource resource) {
        String resourceName = resource.name();
        if (resourceName.endsWith(".txt")) {
            return resourceName.replace('\\', '/');
        }
        return null;
    }

    private static <T> List<T> scanWith(ResourceResolver resolver, Function<Resource, T> mapper) throws IOException {
        List<T> results = resolver.scan(mapper);
        assertNotNull(results, "scan() should never return null");
        return results;
    }

    @Test
    public void scanClass() throws IOException {
        List<String> classes = scanWith(new ResourceResolver(SCAN_BASE_PACKAGE), ResourceResolverTest::mapClassResource);
        Collections.sort(classes);
        String[] expectedClasses = new String[] {
                // list of some scan classes:
                "com.kurna.scan.convert.ValueConverterBean", //
                "com.kurna.scan.destroy.AnnotationDestroyBean", //
                "com.kurna.scan.init.SpecifyInitConfiguration", //
                "com.kurna.scan.proxy.OriginBean", //
                "com.kurna.scan.proxy.FirstProxyBeanPostProcessor", //
                "com.kurna.scan.proxy.SecondProxyBeanPostProcessor", //
                "com.kurna.scan.nested.OuterBean", //
                "com.kurna.scan.nested.OuterBean$NestedBean", //
                "com.kurna.scan.sub1.Sub1Bean", //
                "com.kurna.scan.sub1.sub2.Sub2Bean", //
                "com.kurna.scan.sub1.sub2.sub3.Sub3Bean", //
        };
        for (String expectedClass : expectedClasses) {
            assertTrue(classes.contains(expectedClass), "missing class: " + expectedClass);
        }
    }

    @Test
    public void scanJar() throws IOException {
        List<String> classes = scanWith(new ResourceResolver(PostConstruct.class.getPackageName()), ResourceResolverTest::mapClassResource);
        // classes in jar:
        assertTrue(classes.contains(PostConstruct.class.getName()));
        assertTrue(classes.contains(PreDestroy.class.getName()));
        assertTrue(classes.contains(PermitAll.class.getName()));
        assertTrue(classes.contains(DataSourceDefinition.class.getName()));
        // jakarta.annotation.sub.AnnoScan is defined in classes:
        assertTrue(classes.contains(AnnoScan.class.getName()));
    }

    @Test
    public void scanTxt() throws IOException {
        List<String> classes = scanWith(new ResourceResolver(SCAN_BASE_PACKAGE), ResourceResolverTest::mapTextResource);
        Collections.sort(classes);
        assertIterableEquals(List.of(
            // txt files:
            "com/kurna/scan/sub1/sub1.txt", //
            "com/kurna/scan/sub1/sub2/sub2.txt", //
            "com/kurna/scan/sub1/sub2/sub3/sub3.txt" //
        ), classes);
    }
}
