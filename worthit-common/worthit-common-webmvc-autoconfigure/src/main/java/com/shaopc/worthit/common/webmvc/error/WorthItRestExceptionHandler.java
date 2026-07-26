package com.shaopc.worthit.common.webmvc.error;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import cn.dev33.satoken.exception.SaTokenException;
import com.shaopc.worthit.common.core.error.BusinessException;
import com.shaopc.worthit.common.core.error.ErrorCode;
import com.shaopc.worthit.common.core.trace.TraceIdGenerator;
import com.shaopc.worthit.common.security.error.SecurityErrorCode;
import com.shaopc.worthit.common.security.header.SecurityHeaderNames;
import com.shaopc.worthit.common.web.error.CommonWebErrorCode;
import com.shaopc.worthit.common.web.response.ApiResponse;
import com.shaopc.worthit.common.web.response.FieldViolation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 将 MVC 边界异常转换为 WorthIt 统一 API 响应。
 */
@Order(Ordered.LOWEST_PRECEDENCE)
@RestControllerAdvice
public final class WorthItRestExceptionHandler {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(WorthItRestExceptionHandler.class);

    private final ErrorHttpStatusResolver statusResolver;
    private final TraceIdGenerator traceIdGenerator;

    /**
     * 创建统一 MVC 异常处理器。
     *
     * @param statusResolver 错误码 HTTP 状态解析器
     * @param traceIdGenerator TraceId 生成器
     */
    public WorthItRestExceptionHandler(
            ErrorHttpStatusResolver statusResolver,
            TraceIdGenerator traceIdGenerator) {
        this.statusResolver = Objects.requireNonNull(
                statusResolver, "HTTP状态解析器不能为空");
        this.traceIdGenerator = Objects.requireNonNull(
                traceIdGenerator, "TraceId生成器不能为空");
    }

    /**
     * 转换请求体 Bean Validation 错误。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        return validationResponse(
                request,
                fieldViolations(exception.getBindingResult()));
    }

    /**
     * 转换表单对象绑定错误。
     */
    @ExceptionHandler(BindException.class)
    ResponseEntity<ApiResponse<Void>> handleBind(
            BindException exception,
            HttpServletRequest request) {
        return validationResponse(
                request,
                fieldViolations(exception.getBindingResult()));
    }

    /**
     * 转换方法级 Bean Validation 错误。
     */
    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiResponse<Void>> handleConstraintViolation(
            ConstraintViolationException exception,
            HttpServletRequest request) {
        List<FieldViolation> details = exception
                .getConstraintViolations()
                .stream()
                .sorted(Comparator.comparing(violation ->
                        violation.getPropertyPath().toString()))
                .map(this::fieldViolation)
                .toList();
        return validationResponse(request, details);
    }

    /**
     * 转换 Spring MVC 方法参数校验错误。
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ApiResponse<Void>> handleMethodValidation(
            HandlerMethodValidationException exception,
            HttpServletRequest request) {
        if (exception.isForReturnValue()) {
            return handleUnknown(exception, request);
        }
        List<FieldViolation> details = exception
                .getParameterValidationResults()
                .stream()
                .flatMap(result -> result
                        .getResolvableErrors()
                        .stream()
                        .map(error -> fieldViolation(result, error)))
                .toList();
        return validationResponse(request, details);
    }

    /**
     * 转换请求参数类型错误。
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiResponse<Void>> handleTypeMismatch(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request) {
        return validationResponse(
                request,
                List.of(new FieldViolation(
                        exception.getName(),
                        "参数类型不正确")));
    }

    /**
     * 转换缺失的必填请求参数。
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ApiResponse<Void>> handleMissingParameter(
            MissingServletRequestParameterException exception,
            HttpServletRequest request) {
        return validationResponse(
                request,
                List.of(new FieldViolation(
                        exception.getParameterName(),
                        "不能为空")));
    }

    /**
     * 转换 JSON 结构或类型错误。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiResponse<Void>> handleUnreadableMessage(
            HttpMessageNotReadableException exception,
            HttpServletRequest request) {
        return validationResponse(
                request,
                List.of(new FieldViolation(
                        "requestBody",
                        "请求体格式不正确")));
    }

    /**
     * 转换携带稳定领域错误码的业务异常。
     */
    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ApiResponse<Void>> handleBusinessException(
            BusinessException exception,
            HttpServletRequest request) {
        return response(
                request,
                statusResolver.resolve(exception.errorCode()),
                exception.errorCode(),
                exception.getMessage(),
                List.of());
    }

    /**
     * 转换未登录异常。
     */
    @ExceptionHandler(NotLoginException.class)
    ResponseEntity<ApiResponse<Void>> handleNotLogin(
            NotLoginException exception,
            HttpServletRequest request) {
        return response(
                request,
                HttpStatus.UNAUTHORIZED,
                SecurityErrorCode.AUTH_UNAUTHORIZED,
                SecurityErrorCode.AUTH_UNAUTHORIZED.defaultMessage(),
                List.of());
    }

    /**
     * 转换权限和角色校验异常。
     */
    @ExceptionHandler({
            NotPermissionException.class,
            NotRoleException.class,
            SaTokenException.class
    })
    ResponseEntity<ApiResponse<Void>> handleForbidden(
            SaTokenException exception,
            HttpServletRequest request) {
        return response(
                request,
                HttpStatus.FORBIDDEN,
                SecurityErrorCode.AUTH_FORBIDDEN,
                SecurityErrorCode.AUTH_FORBIDDEN.defaultMessage(),
                List.of());
    }

    /**
     * 转换静态或动态资源不存在异常。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiResponse<Void>> handleNoResource(
            NoResourceFoundException exception,
            HttpServletRequest request) {
        return response(
                request,
                HttpStatus.NOT_FOUND,
                CommonWebErrorCode.RES_NOT_FOUND,
                CommonWebErrorCode.RES_NOT_FOUND.defaultMessage(),
                List.of());
    }

    /**
     * 隐藏未预期异常细节并记录完整服务端日志。
     */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> handleUnknown(
            Exception exception,
            HttpServletRequest request) {
        String traceId = currentOrGeneratedTraceId(request);
        LOGGER.error(
                "未处理的MVC异常 method={}, path={}, traceId={}",
                request.getMethod(),
                request.getRequestURI(),
                traceId,
                exception);
        return response(
                traceId,
                HttpStatus.INTERNAL_SERVER_ERROR,
                CommonWebErrorCode.SYS_ERROR,
                CommonWebErrorCode.SYS_ERROR.defaultMessage(),
                List.of());
    }

    private ResponseEntity<ApiResponse<Void>> validationResponse(
            HttpServletRequest request,
            List<FieldViolation> details) {
        return response(
                request,
                HttpStatus.BAD_REQUEST,
                CommonWebErrorCode.VAL_INVALID_ARGUMENT,
                CommonWebErrorCode.VAL_INVALID_ARGUMENT.defaultMessage(),
                details);
    }

    private ResponseEntity<ApiResponse<Void>> response(
            HttpServletRequest request,
            HttpStatus status,
            ErrorCode errorCode,
            String message,
            List<FieldViolation> details) {
        return response(
                currentOrGeneratedTraceId(request),
                status,
                errorCode,
                message,
                details);
    }

    private ResponseEntity<ApiResponse<Void>> response(
            String traceId,
            HttpStatus status,
            ErrorCode errorCode,
            String message,
            List<FieldViolation> details) {
        return ResponseEntity
                .status(status)
                .header(SecurityHeaderNames.TRACE_ID, traceId)
                .body(ApiResponse.error(
                        errorCode,
                        message,
                        traceId,
                        details));
    }

    private List<FieldViolation> fieldViolations(
            BindingResult bindingResult) {
        return bindingResult
                .getFieldErrors()
                .stream()
                .map(this::fieldViolation)
                .toList();
    }

    private FieldViolation fieldViolation(FieldError fieldError) {
        return new FieldViolation(
                fieldError.getField(),
                messageOrDefault(fieldError.getDefaultMessage()));
    }

    private FieldViolation fieldViolation(
            ConstraintViolation<?> violation) {
        return new FieldViolation(
                violation.getPropertyPath().toString(),
                messageOrDefault(violation.getMessage()));
    }

    private FieldViolation fieldViolation(
            ParameterValidationResult result,
            MessageSourceResolvable error) {
        if (error instanceof FieldError fieldError) {
            return fieldViolation(fieldError);
        }
        return new FieldViolation(
                parameterName(result.getMethodParameter()),
                messageOrDefault(error.getDefaultMessage()));
    }

    private String parameterName(MethodParameter parameter) {
        RequestParam requestParam =
                parameter.getParameterAnnotation(RequestParam.class);
        if (requestParam != null) {
            return firstText(
                    requestParam.name(),
                    requestParam.value(),
                    parameter);
        }
        PathVariable pathVariable =
                parameter.getParameterAnnotation(PathVariable.class);
        if (pathVariable != null) {
            return firstText(
                    pathVariable.name(),
                    pathVariable.value(),
                    parameter);
        }
        RequestHeader requestHeader =
                parameter.getParameterAnnotation(RequestHeader.class);
        if (requestHeader != null) {
            return firstText(
                    requestHeader.name(),
                    requestHeader.value(),
                    parameter);
        }
        return fallbackParameterName(parameter);
    }

    private String firstText(
            String first,
            String second,
            MethodParameter parameter) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return fallbackParameterName(parameter);
    }

    private String fallbackParameterName(MethodParameter parameter) {
        String parameterName = parameter.getParameterName();
        return parameterName == null || parameterName.isBlank()
                ? "arg" + parameter.getParameterIndex()
                : parameterName;
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

    private static String messageOrDefault(String message) {
        return message == null || message.isBlank()
                ? CommonWebErrorCode.VAL_INVALID_ARGUMENT.defaultMessage()
                : message;
    }
}
