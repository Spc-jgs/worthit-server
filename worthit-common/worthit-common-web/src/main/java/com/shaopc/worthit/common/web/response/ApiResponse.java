package com.shaopc.worthit.common.web.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.shaopc.worthit.common.core.error.ErrorCode;

import java.util.List;
import java.util.Objects;

/**
 * 统一 API 响应信封。
 *
 * <p>字段顺序属于序列化契约；失败响应必须保留 {@code data: null}，
 * 空校验详情则不输出。</p>
 *
 * @param success 请求是否成功
 * @param code    稳定机器响应码
 * @param message 中文响应消息
 * @param data    响应数据
 * @param traceId 可信调用链追踪标识
 * @param details 字段校验详情
 * @param <T>     响应数据类型
 */
@JsonPropertyOrder({"success", "code", "message", "data", "traceId", "details"})
public record ApiResponse<T>(
        boolean success,
        String code,
        String message,
        T data,
        String traceId,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<FieldViolation> details) {

    /**
     * 校验响应信封的必填字段并复制校验详情。
     */
    public ApiResponse {
        code = requireText(code, "响应码");
        message = requireText(message, "响应消息");
        traceId = requireText(traceId, "链路追踪标识");
        details = List.copyOf(Objects.requireNonNull(details, "校验详情不能为空"));
    }

    /**
     * 创建成功响应。
     *
     * @param data    响应数据
     * @param traceId 可信调用链追踪标识
     * @param <T>     响应数据类型
     * @return 成功响应信封
     */
    public static <T> ApiResponse<T> success(T data, String traceId) {
        return new ApiResponse<>(
                true,
                ApiResponseConstants.SUCCESS_CODE,
                ApiResponseConstants.SUCCESS_MESSAGE,
                data,
                traceId,
                List.of());
    }

    /**
     * 使用错误码默认消息创建失败响应。
     *
     * @param errorCode 稳定错误码
     * @param traceId   可信调用链追踪标识
     * @param details   字段校验详情
     * @param <T>       响应数据类型
     * @return 失败响应信封
     */
    public static <T> ApiResponse<T> error(
            ErrorCode errorCode,
            String traceId,
            List<FieldViolation> details) {
        ErrorCode requiredErrorCode =
                Objects.requireNonNull(errorCode, "错误码不能为空");
        return error(requiredErrorCode, requiredErrorCode.defaultMessage(), traceId, details);
    }

    /**
     * 使用自定义中文消息创建失败响应。
     *
     * @param errorCode 稳定错误码
     * @param message   自定义中文消息
     * @param traceId   可信调用链追踪标识
     * @param details   字段校验详情
     * @param <T>       响应数据类型
     * @return 失败响应信封
     */
    public static <T> ApiResponse<T> error(
            ErrorCode errorCode,
            String message,
            String traceId,
            List<FieldViolation> details) {
        ErrorCode requiredErrorCode =
                Objects.requireNonNull(errorCode, "错误码不能为空");
        String code = requireText(requiredErrorCode.code(), "错误码编码");
        return new ApiResponse<>(false, code, message, null, traceId, details);
    }

    /**
     * 校验响应文本和机器编码不为空。
     */
    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + "不能为空");
        }
        return value;
    }
}
