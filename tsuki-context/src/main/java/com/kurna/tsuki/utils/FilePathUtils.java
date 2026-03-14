package com.kurna.tsuki.utils;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Map;

public abstract class FilePathUtils {

    /**
     * 将指向 Jar 资源的 URI 转换为可遍历的 {@link Path}
     *
     * @param basePackagePath 基础包对应的路径形式，例如 {@code com/kurna/tsuki}
     * @param jarUri          指向 Jar 包内部资源的 URI，格式如 {@code jar:file:/path/to/foo.jar!/}
     * @return Jar 文件系统中基础包目录对应的 {@link Path}
     * @throws IOException 创建 Jar 文件系统或解析路径时发生 I/O 异常
     */
    public static Path jarUriToPath(String basePackagePath, URI jarUri) throws IOException {
        return FileSystems.newFileSystem(jarUri, Map.of()).getPath(basePackagePath);
    }

    /**
     * 将 URI 转为字符串并按 UTF-8 解码，消除路径中的百分号编码。
     *
     * @param uri 要转换的 URI
     * @return 解码后的 URI 字符串表示
     */
    public static String uriToString(URI uri) {
        return URLDecoder.decode(uri.toString(), StandardCharsets.UTF_8);
    }

    /**
     * 将系统相关的路径分隔符统一转换为正斜线 {@code /}
     *
     * @param pathText 原始路径文本（可能含有 {@code \} 分隔符）
     * @return 将所有 {@code \} 替换为 {@code /} 后的路径文本
     */
    public static String toSlashPath(String pathText) {
        return pathText.replace('\\', '/');
    }

    /**
     * 去掉字符串开头的路径分隔符（{@code /} 或 {@code \}）。
     * <p>
     * 常用于将绝对路径转换为相对路径，或规范化资源名称。
     * 仅去掉第一个字符，若存在连续的分隔符前缀，只移除首个。
     * </p>
     *
     * @param s 原始字符串
     * @return 去掉首个路径分隔符后的字符串；若首字符不是分隔符则返回原值
     */
    public static String removeLeadingSlash(String s) {
        if (s.startsWith("/") || s.startsWith("\\")) {
            s = s.substring(1);
        }
        return s;
    }

    /**
     * 去掉字符串结尾的路径分隔符
     *
     * @param s 原始字符串
     * @return 去掉末尾 {@code /} 或 {@code \} 后的字符串；如果没有尾部分隔符则返回原值
     */
    public static String removeTrailingSlash(String s) {
        if (s.endsWith("/") || s.endsWith("\\")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }
}
