package com.shaopc.worthit.tracking.interfaces.rest;

/**
 * Tracking 公网 HTTP Header 名称。
 */
public final class TrackingHeaderNames {

    /** 写接口幂等键。 */
    public static final String IDEMPOTENCY_KEY =
            "Idempotency-Key";

    private TrackingHeaderNames() {
    }
}
