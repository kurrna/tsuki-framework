package com.kurna.tsuki.io;

/**
 * 类似 ${key:defaultVale} 的占位符表达式
 * @param key
 * @param defaultValue
 */
public record PropertyExpr(String key, String defaultValue) {
}
