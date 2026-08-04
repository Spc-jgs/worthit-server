package com.shaopc.worthit.common.webmvc.error;

import com.shaopc.worthit.common.core.error.ErrorCode;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.Objects;

/**
 * WorthIt 稳定错误码的默认 HTTP 状态映射。
 */
public final class DefaultErrorHttpStatusResolver
        implements ErrorHttpStatusResolver {

    private static final Map<String, HttpStatus> STATUS_BY_CODE = Map.ofEntries(
            Map.entry("VAL_INVALID_ARGUMENT", HttpStatus.BAD_REQUEST),
            Map.entry("RES_NOT_FOUND", HttpStatus.NOT_FOUND),
            Map.entry("AUTH_UNAUTHORIZED", HttpStatus.UNAUTHORIZED),
            Map.entry("AUTH_FORBIDDEN", HttpStatus.FORBIDDEN),
            Map.entry("SYS_ERROR", HttpStatus.INTERNAL_SERVER_ERROR),
            Map.entry("SYS_UPSTREAM", HttpStatus.BAD_GATEWAY),
            Map.entry("DATA_EXPORT_LIMIT_EXCEEDED",
                    HttpStatus.PAYLOAD_TOO_LARGE),
            Map.entry("DATA_EXPORT_BUSY", HttpStatus.TOO_MANY_REQUESTS));

    @Override
    public HttpStatus resolve(ErrorCode errorCode) {
        ErrorCode required =
                Objects.requireNonNull(errorCode, "错误码不能为空");
        return STATUS_BY_CODE.getOrDefault(
                required.code(),
                HttpStatus.CONFLICT);
    }
}
