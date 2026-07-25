package com.shaopc.worthit.common.http.trace;

/**
 * 为当前内部调用提供可信链路追踪标识。
 */
@FunctionalInterface
public interface TraceIdProvider {

    /**
     * 获取当前调用使用的 TraceId。
     *
     * @return 非空的可信 TraceId
     */
    String currentTraceId();
}
