package com.shaopc.worthit.common.core.error;

import java.io.Serial;
import java.util.Objects;

/**
 * 携带稳定错误码的通用业务异常。
 *
 * <p>该异常不绑定 HTTP 状态，由具体接口层负责转换边界语义。</p>
 */
public class BusinessException extends RuntimeException {

    /**
     * Java 序列化版本号。
     */
    @Serial
    private static final long serialVersionUID = 1L;

    private final ErrorCode errorCode;

    /**
     * 使用错误码默认消息创建业务异常。
     *
     * @param errorCode 稳定错误码
     */
    public BusinessException(ErrorCode errorCode) {
        this(errorCode, defaultMessage(errorCode), null);
    }

    /**
     * 使用自定义中文消息创建业务异常。
     *
     * @param errorCode 稳定错误码
     * @param message   自定义异常消息
     */
    public BusinessException(ErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    /**
     * 使用自定义中文消息和原始原因创建业务异常。
     *
     * @param errorCode 稳定错误码
     * @param message   自定义异常消息
     * @param cause     原始异常原因
     */
    public BusinessException(ErrorCode errorCode, String message, Throwable cause) {
        super(requireText(message, "异常消息"), cause);
        this.errorCode = requireErrorCode(errorCode);
    }

    /**
     * 获取完整错误码对象。
     *
     * @return 创建异常时传入的错误码
     */
    public ErrorCode errorCode() {
        return errorCode;
    }

    /**
     * 获取便于边界层转换的机器错误码。
     *
     * @return 稳定错误码编码
     */
    public String code() {
        return errorCode.code();
    }

    /**
     * 校验错误码并获取默认消息。
     */
    private static String defaultMessage(ErrorCode errorCode) {
        return requireErrorCode(errorCode).defaultMessage();
    }

    /**
     * 校验错误码对象及其机器编码。
     */
    private static ErrorCode requireErrorCode(ErrorCode errorCode) {
        ErrorCode required = Objects.requireNonNull(errorCode, "错误码不能为空");
        requireText(required.code(), "错误码编码");
        return required;
    }

    /**
     * 校验人类可读文本或机器编码不为空。
     */
    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + "不能为空");
        }
        return value;
    }
}
