package com.kurna.tsuki.utils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.representer.Representer;
import org.yaml.snakeyaml.resolver.Resolver;

/**
 * YAML 文件解析工具类。
 * <p>
 * 基于 <a href="https://github.com/snakeyaml/snakeyaml">SnakeYAML</a> 实现，
 * 提供从类路径加载 YAML 文件并将其转换为 Java {@link Map} 的工具方法。
 * </p>
 * <p>
 * 默认禁用 SnakeYAML 的隐式类型推断（所有值均以 {@link String} 类型读取），
 * 避免因 YAML 规范的自动类型转换导致布尔值、数字、日期等被意外解析。
 * 所有方法均为静态方法，不支持实例化。
 * </p>
 */
public abstract class YamlUtils {

    /**
     * 从类路径中加载 YAML 文件，返回原始嵌套结构的 {@link Map}。
     * <p>
     * 返回的 Map 保留了 YAML 文件的层级结构：嵌套节点仍为 {@code Map<String, Object>}，
     * 列表节点为 {@code List<Object>}，叶子节点值均为 {@link String}。
     * 若需要扁平化的键值对结构，请使用 {@link #loadYamlAsPlainMap(String)}。
     * </p>
     *
     * @param path 类路径中的 YAML 文件路径，支持以 {@code /} 开头
     * @return 以嵌套 {@link Map} 表示的 YAML 内容
     * @throws java.io.UncheckedIOException 文件不存在或读取失败时抛出
     */
    public static Map<String, Object> loadYaml(String path) {
        var loaderOptions = new LoaderOptions();
        var dumperOptions = new DumperOptions();
        var representer = new Representer(dumperOptions);
        var resolver = new NoImplicitResolver();
        var yaml = new Yaml(new Constructor(loaderOptions), representer, dumperOptions, loaderOptions, resolver);
        return FileReadUtils.readInputStream(path, yaml::load);
    }

    /**
     * 从类路径中加载 YAML 文件，并将其展开为扁平化的键值对 {@link Map}。
     * <p>
     * 嵌套结构中的层级分隔符为点号 {@code .}。例如，以下 YAML：
     * <pre>{@code
     * app:
     *   name: demo
     *   port: 8080
     * }</pre>
     * 会被转换为：
     * <pre>{@code
     * {
     *   "app.name" -> "demo",
     *   "app.port" -> "8080"
     * }
     * }</pre>
     * List 类型的值不会被递归展开，整体作为 {@link List} 保留在对应键下。
     * </p>
     *
     * @param path 类路径中的 YAML 文件路径，支持以 {@code /} 开头
     * @return 扁平化后的键值对 {@link Map}，键使用 {@code .} 拼接层级，值均为 {@link String} 或 {@link List}
     * @throws java.io.UncheckedIOException 文件不存在或读取失败时抛出
     */
    public static Map<String, Object> loadYamlAsPlainMap(String path) {
        Map<String, Object> data = loadYaml(path);
        Map<String, Object> plain = new LinkedHashMap<>();
        convertTo(data, "", plain);
        return plain;
    }

    /**
     * 将嵌套的 YAML {@link Map} 递归展开为扁平化键值对，写入目标 {@code plain} 集合。
     * <p>
     * 遍历 {@code source} 中的每个条目：
     * <ul>
     *   <li>若值为 {@link Map}，则以 {@code prefix + key + "."} 为新前缀递归处理；</li>
     *   <li>若值为 {@link List}，则直接写入 {@code plain}，键为 {@code prefix + key}；</li>
     *   <li>其他值调用 {@code toString()} 后写入 {@code plain}。</li>
     * </ul>
     * </p>
     *
     * @param source 当前层级的嵌套 Map
     * @param prefix 当前层级的键前缀（根层级为空字符串）
     * @param plain  用于收集扁平化结果的目标 Map
     */
    static void convertTo(Map<String, Object> source, String prefix, Map<String, Object> plain) {
        for (String key : source.keySet()) {
            Object value = source.get(key);
            if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> subMap = (Map<String, Object>) value;
                convertTo(subMap, prefix + key + ".", plain);
            } else if (value instanceof List) {
                plain.put(prefix + key, value);
            } else {
                plain.put(prefix + key, value.toString());
            }
        }
    }

    /**
     * 禁用 SnakeYAML 所有隐式类型转换的自定义解析器。
     * <p>
     * 默认情况下，SnakeYAML 会将 {@code true}/{@code false} 自动解析为 {@code Boolean}，
     * 将数字字符串解析为 {@code Integer}/{@code Long}，将日期格式字符串解析为 {@code Date} 等。
     * 此解析器通过清空隐式解析规则，确保所有标量值均保留为 {@link String}，
     * 由上层（如 {@link com.kurna.tsuki.io.PropertyResolver}）的类型转换器统一处理。
     * </p>
     */
    static class NoImplicitResolver extends Resolver {

        public NoImplicitResolver() {
            super();
            super.yamlImplicitResolvers.clear();
        }
    }
}

