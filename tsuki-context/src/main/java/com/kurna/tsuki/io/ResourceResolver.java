package com.kurna.tsuki.io;

import com.kurna.tsuki.exception.ResourceScanException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.kurna.tsuki.utils.FilePathUtils.jarUriToPath;
import static com.kurna.tsuki.utils.FilePathUtils.removeLeadingSlash;
import static com.kurna.tsuki.utils.FilePathUtils.removeTrailingSlash;
import static com.kurna.tsuki.utils.FilePathUtils.toSlashPath;
import static com.kurna.tsuki.utils.FilePathUtils.uriToString;

/**
 * 按基础包路径扫描类路径中的资源，并将扫描到的文件封装为 {@link Resource} 后交给调用方处理。
 * <p>
 * 该类同时兼容普通文件目录和 Jar 包两种资源来源：先基于包名定位资源根路径，再遍历其中的文件，
 * 最后通过 {@code mapper} 将每个 {@code Resource} 映射为调用方需要的结果对象。
 */
public class ResourceResolver {

    /**
     * 当前解析器使用的日志记录器，用于输出扫描路径和发现到的资源等调试信息。
     */
    private final Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * 资源扫描的基础包名，例如 {@code com.kurna.tsuki}。
     * <p>
     * 运行时会将点号分隔的包名转换为路径形式，用于在类路径中查找对应目录。
     */
    private final String basePackage;

    /**
     * 基础包对应的路径形式，例如 {@code com/kurna/tsuki}。
     */
    private final String basePackagePath;

    /**
     * 创建一个资源解析器，并指定要扫描的基础包。
     *
     * @param basePackage 要扫描的基础包名，通常使用 Java 标准包名格式
     */
    public ResourceResolver(String basePackage) {
        this.basePackage = Objects.requireNonNull(basePackage, "basePackage must not be null");
        this.basePackagePath = this.basePackage.replace('.', '/');
    }

    /**
     * 获取用于扫描资源的类加载器。
     * <p>
     * 因为Web应用的ClassLoader不是JVM提供的基于Classpath的ClassLoader，而是Servlet容器提供的ClassLoader，
     * 它不在默认的Classpath搜索，而是在/WEB-INF/classes目录和/WEB-INF/lib的所有jar包搜索，从Thread.getContextClassLoader()可以获取到Servlet容器专属的ClassLoader
     *
     * @return 可用于读取类路径资源的 {@link ClassLoader} 实例
     */
    ClassLoader getContextClassLoader() {
        ClassLoader clazz = Thread.currentThread().getContextClassLoader();
        if (clazz == null) {
            clazz = getClass().getClassLoader();
        }
        return clazz;
    }

    /**
     * 从基础包开始扫描资源，并使用给定的映射函数处理每一个扫描到的资源。
     * <p>
     * 如果 {@code mapper} 返回非 {@code null}，该结果会被加入返回列表；返回 {@code null} 的资源会被忽略。
     * 扫描失败时会抛出 {@link ResourceScanException}，并保留底层异常信息。
     *
     * @param mapper 资源映射函数，接收 {@link Resource} 并返回调用方需要收集的结果
     * @param <R>    返回结果列表中的元素类型
     * @return 映射后的结果列表
     * @throws ResourceScanException 扫描过程中出现 I/O 或 URI 解析异常
     */
    public <R> List<R> scan(Function<Resource, R> mapper) throws ResourceScanException {
        String resourceLookupPath = this.basePackagePath;
        try {
            List<R> mappedResults = new ArrayList<>();
            // 在基础包路径下搜索所有资源，通过 mapper 映射到结果中
            scan0(resourceLookupPath, mappedResults, mapper);
            return mappedResults;
        } catch (IOException | URISyntaxException e) {
            throw new ResourceScanException(
                basePackage,
                resourceLookupPath,
                "failed to scan resources in package: " + basePackage,
                e
            );
        }
    }

    /**
     * 执行实际的资源定位与分发逻辑。
     * <p>
     * 该方法会先通过类加载器获取指定路径下的所有资源根地址，再根据 URI 判断资源位于普通文件系统
     * 还是 Jar 文件中，最后统一交由 {@link #scanFile(boolean, String, Path, List, Function)} 继续遍历。
     *
     * @param resourceLookupPath 当前要交给类加载器查找的资源路径
     * @param mappedResults      用于收集 {@code mapper} 处理结果的列表
     * @param mapper             资源映射函数
     * @param <R>                收集结果的元素类型
     * @throws IOException        访问文件系统或 Jar 文件系统时发生 I/O 异常
     * @throws URISyntaxException 将资源 URL 转换为 URI 时发生格式错误
     */
    <R> void scan0(String resourceLookupPath,
                   List<R> mappedResults, Function<Resource, R> mapper)
        throws IOException, URISyntaxException {
        logger.atDebug().log("scan path: {}", resourceLookupPath);
        // 通过读取当前的类加载器，获取指定路径下的所有资源地址（可能有多个 Jar 包或目录都包含该路径）
        Enumeration<URL> resourceUrls = getContextClassLoader().getResources(resourceLookupPath);
        while (resourceUrls.hasMoreElements()) {
            URL resourceUrl = resourceUrls.nextElement();
            URI resourceUri = resourceUrl.toURI();
            String decodedResourceUriText = removeTrailingSlash(uriToString(resourceUri));
            String resourceRootText = decodedResourceUriText.substring(
                0,
                decodedResourceUriText.length() - basePackagePath.length()
            );
            if (resourceRootText.startsWith("file:")) {
                resourceRootText = resourceRootText.substring(5);
            }
            if (decodedResourceUriText.startsWith("jar:")) {
                scanFile(true, resourceRootText, jarUriToPath(basePackagePath, resourceUri), mappedResults, mapper);
            } else {
                scanFile(false, resourceRootText, Paths.get(resourceUri), mappedResults, mapper);
            }
        }
    }





    /**
     * 遍历给定根路径下的所有普通文件，并将其转换为 {@link Resource} 后交给映射函数处理。
     * <p>
     * 当资源来自 Jar 包时，{@code Resource#path()} 记录的是 Jar 内部资源的基础路径；
     * 当资源来自普通文件系统时，{@code Resource#path()} 会以 {@code file:} 前缀保存文件绝对路径，
     * 同时计算出相对于基础目录的资源名作为 {@code Resource#name()}。
     *
     * @param isJar             当前遍历的资源是否来自 Jar 文件
     * @param resourceRootText  资源根路径，用于构造 {@link Resource} 的路径或相对名称
     * @param traversalRootPath 实际要遍历的根目录路径
     * @param mappedResults     用于收集处理结果的列表
     * @param mapper            资源映射函数
     * @param <R>               收集结果的元素类型
     * @throws IOException 遍历文件树时发生 I/O 异常
     */
    <R> void scanFile(boolean isJar, String resourceRootText, Path traversalRootPath,
                      List<R> mappedResults, Function<Resource, R> mapper)
        throws IOException {
        String normalizedResourceRoot = removeTrailingSlash(resourceRootText);
        try (Stream<Path> stream = Files.walk(traversalRootPath)) {
            stream.filter(Files::isRegularFile).forEach(resourceFilePath -> {
                Resource resource;
                if (isJar) {
                    String resourceName = removeLeadingSlash(toSlashPath(resourceFilePath.toString()));
                    resource = new Resource(normalizedResourceRoot, resourceName);
                } else {
                    String absoluteResourcePathText = resourceFilePath.toString();
                    String relativeResourcePath = toSlashPath(traversalRootPath.relativize(resourceFilePath).toString());
                    String resourceName = basePackagePath + "/" + removeLeadingSlash(relativeResourcePath);
                    resource = new Resource("file:" + absoluteResourcePathText, resourceName);
                }
                logger.atDebug().log("found resource: {}", resource);
                R r = mapper.apply(resource);
                if (r != null) {
                    mappedResults.add(r);
                }
            });
        }
    }
}
