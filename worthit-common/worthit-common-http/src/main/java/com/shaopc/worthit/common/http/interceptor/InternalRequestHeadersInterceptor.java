package com.shaopc.worthit.common.http.interceptor;

import com.shaopc.worthit.common.http.context.InternalRequestContext;
import com.shaopc.worthit.common.security.header.SecurityHeaderNames;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.util.Objects;

/**
 * 覆盖写入内部服务调用所需的可信请求头。
 */
public final class InternalRequestHeadersInterceptor
        implements ClientHttpRequestInterceptor {

    private final InternalRequestContext requestContext;

    /**
     * 创建内部请求头拦截器。
     *
     * @param requestContext 当前调用方上下文
     */
    public InternalRequestHeadersInterceptor(InternalRequestContext requestContext) {
        this.requestContext = Objects.requireNonNull(
                requestContext, "内部请求上下文不能为空");
    }

    @Override
    public ClientHttpResponse intercept(
            HttpRequest request,
            byte[] body,
            ClientHttpRequestExecution execution) throws IOException {
        request.getHeaders().set(
                SecurityHeaderNames.SAME_TOKEN,
                requireText(
                        requestContext.sameTokenProvider().currentToken(),
                        "Same-Token"));
        request.getHeaders().set(
                SecurityHeaderNames.CALLER_SERVICE,
                requestContext.callerService());
        request.getHeaders().set(
                SecurityHeaderNames.TRACE_ID,
                requireText(
                        requestContext.traceIdProvider().currentTraceId(),
                        "TraceId"));
        return execution.execute(request, body);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + "不能为空");
        }
        return value;
    }
}
