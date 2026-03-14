package com.kurna.tsuki.io;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Properties;

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

public class PropertyResolverTest {

    @Test
    public void propertyValue() {
        var pr = getPropertyResolver();
        assertEquals("Tsuki Framework", pr.getProperty("app.title"));
        assertEquals("v1.0", pr.getProperty("app.version"));
        assertEquals("v1.0", pr.getProperty("app.version", "unknown"));
        assertNull(pr.getProperty("app.author"));
        assertEquals("kurrna", pr.getProperty("app.author", "kurrna"));

        assertEquals(Boolean.TRUE, pr.getProperty("jdbc.auto-commit", boolean.class));
        assertEquals(Boolean.TRUE, pr.getProperty("jdbc.auto-commit", Boolean.class));
        assertTrue(pr.getProperty("jdbc.detect-leak", boolean.class, true));

        assertEquals(20, pr.getProperty("jdbc.pool-size", int.class));
        assertEquals(20, pr.getProperty("jdbc.pool-size", int.class, 999));
        assertEquals(5, pr.getProperty("jdbc.idle", int.class, 5));

        assertEquals(LocalDateTime.parse("2026-03-14T21:45:01"), pr.getProperty("scheduler.started-at", LocalDateTime.class));
        assertEquals(LocalTime.parse("03:05:10"), pr.getProperty("scheduler.backup-at", LocalTime.class));
        assertEquals(LocalTime.parse("23:59:59"), pr.getProperty("scheduler.restart-at", LocalTime.class, LocalTime.parse("23:59:59")));
        assertEquals(Duration.ofMinutes((2 * 24 + 8) * 60 + 21), pr.getProperty("scheduler.cleanup", Duration.class));
        assertEquals(LocalDate.parse("2026-03-14"), pr.getProperty("scheduler.date", LocalDate.class));
        assertEquals(ZoneId.of("Asia/Shanghai"), pr.getProperty("scheduler.zone", ZoneId.class));
        assertEquals(ZonedDateTime.parse("2026-03-14T21:45:01+08:00[Asia/Shanghai]"), pr.getProperty("scheduler.zoned-at", ZonedDateTime.class));
    }

    private static @NonNull PropertyResolver getPropertyResolver() {
        var props = new Properties();
        props.setProperty("app.title", "Tsuki Framework");
        props.setProperty("app.version", "v1.0");
        props.setProperty("jdbc.url", "jdbc:mysql://localhost:3306/simpsons");
        props.setProperty("jdbc.username", "bart");
        props.setProperty("jdbc.password", "51mp50n");
        props.setProperty("jdbc.pool-size", "20");
        props.setProperty("jdbc.auto-commit", "true");
        props.setProperty("scheduler.started-at", "2026-03-14T21:45:01");
        props.setProperty("scheduler.backup-at", "03:05:10");
        props.setProperty("scheduler.cleanup", "P2DT8H21M");
        props.setProperty("scheduler.date", "2026-03-14");
        props.setProperty("scheduler.zone", "Asia/Shanghai");
        props.setProperty("scheduler.zoned-at", "2026-03-14T21:45:01+08:00[Asia/Shanghai]");

        return new PropertyResolver(props);
    }

    @Test
    public void requiredProperty() {
        var props = new Properties();
        props.setProperty("app.title", "Tsuki Framework");
        props.setProperty("app.version", "v1.0");

        var pr = new PropertyResolver(props);
        assertThrows(NullPointerException.class, () -> pr.getRequiredProperty("not.exist"));
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    public void propertyHolder() {
        String userprofile = System.getenv("USERPROFILE");
        System.out.println("env USERPROFILE=" + userprofile);

        var props = new Properties();
        props.setProperty("app.title", "Tsuki Framework");

        var pr = new PropertyResolver(props);
        assertEquals("Tsuki Framework", pr.getProperty("${app.title}"));
        assertThrows(NullPointerException.class, () -> pr.getProperty("${app.version}"));
        assertEquals("v1.0", pr.getProperty("${app.version:v1.0}"));
        assertEquals(1, pr.getProperty("${app.version:1}", int.class));
        assertThrows(NumberFormatException.class, () -> pr.getProperty("${app.version:x}", int.class));

        assertEquals(userprofile, pr.getProperty("${app.path:${USERPROFILE}}"));
        assertEquals(userprofile, pr.getProperty("${app.path:${app.userprofile:${USERPROFILE}}}"));
        assertEquals("/not-exist", pr.getProperty("${app.path:${app.userprofile:${ENV_NOT_EXIST:/not-exist}}}"));
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    public void propertyHolderOnWin() {
        String os = System.getenv("OS");
        System.out.println("env OS=" + os);

        var props = new Properties();
        var pr = new PropertyResolver(props);
        assertEquals("Windows_NT", pr.getProperty("${app.os:${OS}}"));
    }

    @Test
    public void registerCustomConverter() {
        var props = new Properties();
        props.setProperty("app.list", "a,b,c");
        var pr = new PropertyResolver(props);
        pr.registerConverter(String[].class, value -> value.split(","));

        assertArrayEquals(new String[] { "a", "b", "c" }, pr.getProperty("app.list", String[].class));
    }
}
