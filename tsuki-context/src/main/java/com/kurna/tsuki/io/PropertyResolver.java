package com.kurna.tsuki.io;

import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;

/**
 * 属性解析器。
 * <p>
 * 该类将系统环境变量与外部传入的 {@link Properties} 合并后提供统一访问能力，并支持：
 * <ul>
 *   <li>普通键读取：{@code app.name}</li>
 *   <li>占位符读取：{@code ${app.name}}</li>
 *   <li>带默认值占位符：{@code ${app.name:default}}</li>
 *   <li>类型转换读取：{@code getProperty(key, Integer.class)}</li>
 * </ul>
 * 不支持 {@code #{...}} 表达式。
 */
public class PropertyResolver {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * 改为 propertyMap 避免与 Properties 类混淆
     */
    private final Map<String, String> propertyMap = new HashMap<>();

    private final Map<Class<?>, Function<String, Object>> converterMap = new HashMap<>();

    /**
     * 创建属性解析器。
     * <p>
     * 初始化顺序如下：
     * <ol>
     *   <li>先加载 {@link System#getenv()} 作为基础属性；</li>
     *   <li>再加载传入的 {@link Properties}，并覆盖同名环境变量；</li>
     *   <li>注册默认类型转换器；</li>
     *   <li>在 debug 级别输出最终属性列表。</li>
     * </ol>
     *
     * @param properties 外部传入的配置集合，不能为空
     */
    public PropertyResolver(Properties properties) {
        // 存入所有环境变量
        this.propertyMap.putAll(System.getenv());
        // 存入 Properties
        Set<String> names = properties.stringPropertyNames();
        for (String name : names) {
            this.propertyMap.put(name, properties.getProperty(name));
        }
        registerDefaultConverters();
        logLoadedProperties();
    }

    // 在 PropertyResolver 内部通过 Map 存储所有配置项
    // 实现 getProperty 和 containsProperty

    /**
     * 按字符串形式读取属性值。
     * <p>
     * 读取规则：
     * <ol>
     *   <li>若入参本身是占位符（如 {@code ${key}} 或 {@code ${key:default}}），按占位符语义解析；</li>
     *   <li>否则将其视为普通键，从属性集合中取值；</li>
     *   <li>若取到的值本身也是占位符文本，会继续进行一次占位符解析。</li>
     * </ol>
     *
     * @param key 属性键或占位符表达式
     * @return 解析后的字符串值；不存在时返回 {@code null}
     */
    @Nullable
    public String getProperty(String key) {
        PropertyExpr expression = parsePropertyExpression(key);
        if (expression == null) {
            return parseDirectKey(key);
        }
        return parseExpr(expression);
    }

    /**
     * @param key          属性键或占位符表达式
     * @param defaultValue 默认值（可为普通文本或占位符表达式）
     * @return 解析后的属性值；若属性不存在则返回解析后的默认值
     */
    public String getProperty(String key, String defaultValue) {
        String value = getProperty(key);
        return value == null ? parseValue(defaultValue) : value;
    }

    /**
     * 读取属性并转换为指定类型。
     *
     * @param key        属性键或占位符表达式
     * @param targetType 目标类型
     * @param <T>        目标类型泛型
     * @return 转换后的值；若属性不存在则返回 {@code null}
     */
    @Nullable
    public <T> T getProperty(String key, Class<T> targetType) {
        String value = getProperty(key);
        if (value == null) {
            return null;
        }
        return convertValue(targetType, value);
    }

    /**
     * 读取属性并转换为指定类型，不存在时返回默认值。
     *
     * @param key          属性键或占位符表达式
     * @param targetType   目标类型
     * @param defaultValue 默认值（当属性不存在时返回）
     * @param <T>          目标类型泛型
     * @return 转换后的属性值；若属性不存在则返回 {@code defaultValue}
     */
    public <T> T getProperty(String key, Class<T> targetType, T defaultValue) {
        String value = getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        return convertValue(targetType, value);
    }

    /**
     * 使用已注册转换器执行字符串到目标类型的转换。
     *
     * @param clazz 目标类型
     * @param value 原始字符串值
     * @param <T>   目标类型泛型
     * @return 转换后的对象
     */
    @SuppressWarnings("unchecked")
    <T> T convertValue(Class<?> clazz, String value) {
        Function<String, Object> fn = this.converterMap.get(clazz);
        if (fn == null) {
            throw new IllegalArgumentException("Unsupported target type: " + clazz.getName());
        }
        return (T) fn.apply(value);
    }

    /**
     * @param key 属性键
     * @return 存在返回 {@code true}，否则返回 {@code false}
     */
    public boolean containsProperty(String key) {
        return this.propertyMap.containsKey(key);
    }

    /**
     * 将文本解析为占位符表达式对象。
     *
     * @param text 待解析文本，常见格式为 ${key} 或 ${key:default}，例如 ${app.name}、${app.name:demo}
     * @return 当文本符合占位符格式时返回 {@link PropertyExpr}，否则返回 {@code null}
     */
    PropertyExpr parsePropertyExpression(String text) {
        if (text == null || !text.startsWith("${") || !text.endsWith("}")) {
            return null;
        }
        String body = text.substring(2, text.length() - 1);
        int separator = body.indexOf(':');
        if (separator < 0) {
            return new PropertyExpr(body, null);
        }
        String key = body.substring(0, separator);
        String defaultValue = body.substring(separator + 1);
        return new PropertyExpr(key, defaultValue);
    }

    // 解析 ${abc.xyz:defaultValue} 占位符表达式

    /**
     * 不存在占位符表达式，直接解析 key
     *
     * @param key 传入的key
     * @return value
     */
    @Nullable
    private String parseDirectKey(String key) {
        String rawValue = this.propertyMap.get(key);
        return rawValue == null ? null : parseValue(rawValue);
    }

    /**
     * 解析占位符表达式（递归）。
     * <p>
     * 规则：<br>
     * 1) 先按 key 取值；<br>
     * 2) 取不到且有 defaultValue 时，递归解析 defaultValue；<br>
     * 3) 仍取不到则抛出缺失必需属性异常。
     *
     * @param expr 传入的占位符表达式对象（如 ${app.name}、${app.name:demo}）
     * @return 解析结果
     */
    private String parseExpr(PropertyExpr expr) {
        String byKey = parseDirectKey(expr.key());
        if (byKey != null) {
            return byKey;
        }

        String defaultValue = expr.defaultValue();
        if (defaultValue != null) {
            return parseValue(defaultValue);
        }

        throw new NullPointerException("Property '" + expr.key() + "' not found");
    }

    /**
     * 解析属性值文本。
     * <p>
     * 当值为占位符表达式时返回其解析结果，否则返回原值。
     *
     * @param value 待解析文本，可能是占位符表达式（如 ${key} 或 ${key:default}）或普通文本如 8080
     * @return 解析后的值
     */
    String parseValue(String value) {
        PropertyExpr expression = parsePropertyExpression(value);
        if (expression == null) {
            return value;
        }
        return parseExpr(expression);
    }

    // 初始化相关方法

    private void logLoadedProperties() {
        if (logger.isDebugEnabled()) {
            List<String> keys = new ArrayList<>(this.propertyMap.keySet());
            Collections.sort(keys);
            for (String key : keys) {
                logger.debug("PropertyResolver: {} = {}", key, this.propertyMap.get(key));
            }
        }
    }

    private void registerDefaultConverters() {
        registerConverter(String.class, s -> s);

        registerConverter(boolean.class, Boolean::parseBoolean);
        registerConverter(Boolean.class, Boolean::valueOf);

        registerConverter(byte.class, Byte::parseByte);
        registerConverter(Byte.class, Byte::valueOf);

        registerConverter(short.class, Short::parseShort);
        registerConverter(Short.class, Short::valueOf);

        registerConverter(int.class, Integer::parseInt);
        registerConverter(Integer.class, Integer::valueOf);

        registerConverter(long.class, Long::parseLong);
        registerConverter(Long.class, Long::valueOf);

        registerConverter(float.class, Float::parseFloat);
        registerConverter(Float.class, Float::valueOf);

        registerConverter(double.class, Double::parseDouble);
        registerConverter(Double.class, Double::valueOf);

        registerConverter(LocalDate.class, LocalDate::parse);
        registerConverter(LocalTime.class, LocalTime::parse);
        registerConverter(LocalDateTime.class, LocalDateTime::parse);
        registerConverter(ZonedDateTime.class, ZonedDateTime::parse);
        registerConverter(Duration.class, Duration::parse);
        registerConverter(ZoneId.class, ZoneId::of);
    }

    /**
     * 注册或覆盖指定类型的转换器。
     *
     * @param targetType 目标类型
     * @param converter  将字符串转换为目标类型的函数
     * @param <T>        目标类型
     */
    public <T> void registerConverter(Class<T> targetType, Function<String, T> converter) {
        Objects.requireNonNull(targetType, "targetType must not be null");
        Objects.requireNonNull(converter, "converter must not be null");
        this.converterMap.put(targetType, converter::apply);
    }
}

