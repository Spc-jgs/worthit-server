package com.shaopc.worthit.common.webmvc.error;

import com.shaopc.worthit.common.core.trace.TraceIdGenerator;
import com.shaopc.worthit.common.http.error.RemoteServiceException;
import com.shaopc.worthit.common.security.header.SecurityHeaderNames;
import com.shaopc.worthit.common.web.error.CommonWebErrorCode;
import com.shaopc.worthit.common.web.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.Objects;

/**
 * 将内部 HTTP Client 失败转换为安全的统一上游错误响应。
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public final class RemoteServiceExceptionHandler {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(RemoteServiceExceptionHandler.class);

    private final TraceIdGenerator traceIdGenerator;

    /**
     * 创建远程服务异常处理器。
     *
     * @param traceIdGenerator TraceId 生成器
     */
    public RemoteServiceExceptionHandler(
            TraceIdGenerator traceIdGenerator) {
        this.traceIdGenerator = Objects.requireNonNull(
                traceIdGenerator, "TraceId生成器不能为空");
    }

    /**
     * 转换远程服务异常，保留 502/503 传输语义但不泄露远端内部标识。
     */
    @ExceptionHandler(RemoteServiceException.class)
    ResponseEntity<ApiResponse<Void>> handleRemoteService(
            RemoteServiceException exception,
            HttpServletRequest request) {
        String traceId = currentOrGeneratedTraceId(request);
        HttpStatus status = exception.statusCode()
                == HttpStatus.SERVICE_UNAVAILABLE.value()
                ? HttpStatus.SERVICE_UNAVAILABLE
                : HttpStatus.BAD_GATEWAY;
        LOGGER.warn(
                "下游服务调用失败 target={}, status={}, code={}, "
                        + "remoteTraceId={}, traceId={}",
                exception.targetService(),
                exception.statusCode(),
                exception.remoteCode(),
                exception.remoteTraceId(),
                traceId);
        return ResponseEntity
                .status(status)
                .header(SecurityHeaderNames.TRACE_ID, traceId)
                .body(ApiResponse.error(
                        CommonWebErrorCode.SYS_UPSTREAM,
                        exception.getMessage(),
                        traceId,
                        List.of()));
    }

    private String currentOrGeneratedTraceId(HttpServletRequest request) {
        Object current = request.getAttribute(SecurityHeaderNames.TRACE_ID);
        if (current instanceof String traceId && !traceId.isBlank()) {
            return traceId;
        }
        String generated = traceIdGenerator.generate();
        if (generated == null || generated.isBlank()) {
            throw new IllegalStateException("TraceId不能为空");
        }
        request.setAttribute(SecurityHeaderNames.TRACE_ID, generated);
        return generated;
    }
}
