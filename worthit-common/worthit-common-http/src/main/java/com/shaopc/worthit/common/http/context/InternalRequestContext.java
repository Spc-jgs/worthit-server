package com.shaopc.worthit.common.http.context;

import com.shaopc.worthit.common.http.trace.TraceIdProvider;
import com.shaopc.worthit.common.security.sametoken.SameTokenProvider;

import java.util.Objects;

/**
 * 描述内部 HTTP 请求需要注入的可信调用方上下文。
 *
 * @param callerService    调用方服务名称
 * @param sameTokenProvider Same-Token 提供器
 * @param traceIdProvider   TraceId 提供器
 */
public record InternalRequestContext(
        String callerService,
        SameTokenProvider sameTokenProvider,
        TraceIdProvider traceIdProvider) {

    /**
     * 校验调用方名称和两个上下文提供器。
     */
    public InternalRequestContext {
        if (callerService == null || callerService.isBlank()) {
            throw new IllegalArgumentException("调用方服务名称不能为空");
        }
        sameTokenProvider = Objects.requireNonNull(
                sameTokenProvider, "Same-Token提供器不能为空");
        traceIdProvider = Objects.requireNonNull(
                traceIdProvider, "TraceId提供器不能为空");
    }
}
