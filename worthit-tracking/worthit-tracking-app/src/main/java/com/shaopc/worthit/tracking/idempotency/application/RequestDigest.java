package com.shaopc.worthit.tracking.idempotency.application;

/**
 * 生成 Tracking 写命令的稳定摘要。
 */
@FunctionalInterface
public interface RequestDigest {

    /**
     * 生成十六进制 SHA-256 摘要。
     */
    String hash(Object command);
}
