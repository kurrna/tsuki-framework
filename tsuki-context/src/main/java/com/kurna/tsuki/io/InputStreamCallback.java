package com.kurna.tsuki.io;

import java.io.IOException;
import java.io.InputStream;

/**
 * 回调式的 InputStream 处理器函数接口
 *
 * @param <T> 回调返回的值类型
 */
@FunctionalInterface
public interface InputStreamCallback<T> {

    /**
     * 使用传入的 {@link InputStream} 执行自定义处理并返回结果。
     *
     * @param stream 待处理的输入流（由调用方打开并通常由调用方关闭）
     * @return 处理结果，类型为 {@code T}
     * @throws IOException 当读取或处理流时发生 I/O 错误时抛出
     */
    T doWithInputStream(InputStream stream) throws IOException;
}
