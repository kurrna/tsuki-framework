package com.kurna.tsuki.utils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import com.kurna.tsuki.io.InputStreamCallback;

public abstract class FileReadUtils {

    /**
     * 从类路径中打开指定文件的输入流，并将其交给回调处理后返回结果
     *
     * @param path                路径支持以 {@code /} 开头（会自动去除前导斜线）
     * @param inputStreamCallback 对输入流进行处理并返回结果的回调函数
     * @param <T>                 回调返回的结果类型
     * @return 回调函数的处理结果
     */
    public static <T> T readInputStream(String path, InputStreamCallback<T> inputStreamCallback) {
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        try (InputStream input = getContextClassLoader().getResourceAsStream(path)) {
            if (input == null) {
                throw new FileNotFoundException("File not found in classpath: " + path);
            }
            return inputStreamCallback.doWithInputStream(input);
        } catch (IOException e) {
            System.err.println("Failed to read file from classpath: " + path);
            throw new UncheckedIOException(e);
        }
    }

    /**
     * 从类路径中读取指定文件的全部内容，以 UTF-8 编码解码后返回字符串。
     *
     * @param path 类路径中的文件路径，支持以 {@code /} 开头
     * @return 文件内容字符串
     */
    public static String readString(String path) {
        return readInputStream(path, (input) -> {
            byte[] data = input.readAllBytes();
            return new String(data, StandardCharsets.UTF_8);
        });
    }

    static ClassLoader getContextClassLoader() {
        ClassLoader clazz;
        clazz = Thread.currentThread().getContextClassLoader();
        if (clazz == null) {
            clazz = FileReadUtils.class.getClassLoader();
        }
        return clazz;
    }
}
