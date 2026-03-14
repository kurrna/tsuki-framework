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

    private final Map<String, String> properties = new HashMap<>();
    private final Map<Class<?>, Function<String, Object>> converters = new HashMap<>();

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
        Objects.requireNonNull(properties, "properties must not be null");
        this.properties.putAll(System.getenv());
        Set<String> names = properties.stringPropertyNames();
        for (String name : names) {
            this.properties.put(name, properties.getProperty(name));
        }
        registerDefaultConverters();
        logLoadedProperties();
    }

    private void logLoadedProperties() {
        if (logger.isDebugEnabled()) {
            List<String> keys = new ArrayList<>(this.properties.keySet());
            Collections.sort(keys);
            for (String key : keys) {
                logger.debug("PropertyResolver: {} = {}", key, this.properties.get(key));
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
     * @param converter 将字符串转换为目标类型的函数
     * @param <T> 目标类型
     */
    public <T> void registerConverter(Class<T> targetType, Function<String, T> converter) {
        Objects.requireNonNull(targetType, "targetType must not be null");
        Objects.requireNonNull(converter, "converter must not be null");
        this.converters.put(targetType, converter::apply);
    }

    /**
     * @param key 属性键
     * @return 存在返回 {@code true}，否则返回 {@code false}
     */
    public boolean containsProperty(String key) {
        return this.properties.containsKey(key);
    }

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
        PropertyExpr expression = parsePropertyExpr(key);
        if (expression != null) {
            return resolveExpression(expression);
        }
        return resolveRawProperty(key);
    }

    /**
     * @param key 属性键或占位符表达式
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
     * @param key 属性键或占位符表达式
     * @param targetType 目标类型
     * @param <T> 目标类型泛型
     * @return 转换后的值；若属性不存在则返回 {@code null}
     */
    @Nullable
    public <T> T getProperty(String key, Class<T> targetType) {
        String value = getProperty(key);
        if (value == null) {
            return null;
        }
        // 转换为指定类型
        return convert(targetType, value);
    }

    /**
     * 读取属性并转换为指定类型，不存在时返回默认值。
     *
     * @param key 属性键或占位符表达式
     * @param targetType 目标类型
     * @param defaultValue 默认值（当属性不存在时返回）
     * @param <T> 目标类型泛型
     * @return 转换后的属性值；若属性不存在则返回 {@code defaultValue}
     */
    public <T> T getProperty(String key, Class<T> targetType, T defaultValue) {
        String value = getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        return convert(targetType, value);
    }

    /**
     * 读取必需属性。
     *
     * @param key 属性键或占位符表达式
     * @return 属性值
     * @throws NullPointerException 当属性不存在时抛出
     */
    public String getRequiredProperty(String key) {
        String value = getProperty(key);
        return Objects.requireNonNull(value, "Property '"+ key + "' not found");
    }

    @Nullable
    private String resolveRawProperty(String key) {
        String rawValue = this.properties.get(key);
        return rawValue == null ? null : parseValue(rawValue);
    }

    /**
     * 解析占位符表达式。
     * @param expr 占位符表达式
     * @return 解析结果
     */
    private String resolveExpression(PropertyExpr expr) {
        if (expr.defaultValue() != null) {
            return getProperty(expr.key(), expr.defaultValue());
        }
        return getRequiredProperty(expr.key());
    }

    /**
     * 使用已注册转换器执行字符串到目标类型的转换。
     *
     * @param clazz 目标类型
     * @param value 原始字符串值
     * @param <T> 目标类型泛型
     * @return 转换后的对象
     */
    @SuppressWarnings("unchecked")
    <T> T convert(Class<?> clazz, String value) {
        Function<String, Object> fn = this.converters.get(clazz);
        if (fn == null) {
            throw new IllegalArgumentException("Unsupported target type: " + clazz.getName());
        }
        return (T) fn.apply(value);
    }

    /**
     * 解析属性值文本。
     * <p>
     * 当值为占位符表达式时返回其解析结果，否则返回原值。
     *
     * @param value 原始值
     * @return 解析后的值
     */
    String parseValue(String value) {
        PropertyExpr expression = parsePropertyExpr(value);
        if (expression == null) {
            return value;
        }
        return resolveExpression(expression);
    }

    /**
     * 将文本解析为占位符表达式对象。
     *
     * @param text 待解析文本
     * @return 当文本符合占位符格式时返回 {@link PropertyExpr}，否则返回 {@code null}
     */
    PropertyExpr parsePropertyExpr(String text) {
        if (text.startsWith("${") && text.endsWith("}")) {
            // 是否存在 defaultValue
            int n = text.indexOf(":");
            if (n == (-1)) {
                String k = text.substring(2, text.length() - 1);
                return new PropertyExpr(k, null);
            } else {
                String k = text.substring(2, n);
                return new PropertyExpr(k, text.substring(n + 1, text.length() - 1));
            }
        }
        return null;
    }

}

