package com.kurna.tsuki.exception;

/**
 * AOP 配置异常。
 * <p>
 * 当代理处理器缺失、类型不匹配或注解元数据不满足约定时抛出。
 */
public class AopConfigException extends NestedRuntimeException {

    /**
     * 创建不带错误信息的异常。
     */
    public AopConfigException() {
        super();
    }

    /**
     * 创建包含错误信息和根因的异常。
     *
     * @param message 错误信息
     * @param cause 根因异常
     */
    public AopConfigException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 创建仅包含错误信息的异常。
     *
     * @param message 错误信息
     */
    public AopConfigException(String message) {
        super(message);
    }

    /**
     * 创建仅包含根因的异常。
     *
     * @param cause 根因异常
     */
    public AopConfigException(Throwable cause) {
        super(cause);
    }
}
