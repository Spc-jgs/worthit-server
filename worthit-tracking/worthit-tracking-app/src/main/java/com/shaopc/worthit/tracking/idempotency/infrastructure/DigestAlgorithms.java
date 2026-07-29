package com.shaopc.worthit.tracking.idempotency.infrastructure;

/**
 * 幂等与恢复摘要使用的 JCA 算法名称。
 */
final class DigestAlgorithms {

    static final String SHA_256 = "SHA-256";

    private DigestAlgorithms() {
    }
}
