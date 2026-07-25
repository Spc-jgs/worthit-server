package com.shaopc.worthit.common.core.trace;

import java.util.UUID;

/**
 * 使用 JDK UUID 生成链路追踪标识。
 *
 * <p>输出固定为 32 位小写十六进制字符，不包含连字符。TraceId 是不透明标识，
 * 调用方不得从中推导时间或业务语义。</p>
 */
public final class UuidTraceIdGenerator implements TraceIdGenerator {

    /**
     * 生成新的 UUID TraceId。
     *
     * @return 32 位小写十六进制 TraceId
     */
    @Override
    public String generate() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
