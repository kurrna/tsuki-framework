package com.kurna.tsuki.exception;

import java.io.IOException;

/**
 * 资源扫描过程中抛出的统一异常。
 * <p>
 * 该异常保留了基础包与查找路径信息，便于上层记录日志和定位问题。
 */
public class ResourceScanException extends IOException {

    private final String basePackage;
    private final String resourceLookupPath;

    public ResourceScanException(String basePackage, String resourceLookupPath, String message, Throwable cause) {
        super(message, cause);
        this.basePackage = basePackage;
        this.resourceLookupPath = resourceLookupPath;
    }

    public String getBasePackage() {
        return basePackage;
    }

    public String getResourceLookupPath() {
        return resourceLookupPath;
    }
}

