package com.shaopc.worthit.common.core.trace;

/**
 * 生成服务端可信链路追踪标识。
 *
 * <p>调用方不得把外部请求携带的同名请求头直接当作本接口的生成结果。</p>
 */
@FunctionalInterface
public interface TraceIdGenerator {

    /**
     * 生成新的不透明链路追踪标识。
     *
     * @return 非空的可信链路追踪标识
     */
    String generate();
}
