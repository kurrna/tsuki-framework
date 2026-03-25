package com.kurna.tsuki.utils;

import java.util.Objects;

import com.kurna.tsuki.context.ApplicationContext;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public class ApplicationContextUtils {

    private static ApplicationContext applicationContext = null;

    @Nonnull
    public static ApplicationContext getRequiredApplicationContext() {
        return Objects.requireNonNull(getApplicationContext(), "ApplicationContext is not set.");
    }

    @Nullable
    public static ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    public static void setApplicationContext(ApplicationContext ctx) {
        applicationContext = ctx;
    }
}
