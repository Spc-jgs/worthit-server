package com.shaopc.worthit.tracking.item.application;

/**
 * Item 写接口幂等占位结果。
 *
 * @param status 占位状态
 * @param replay 首次调用结果；仅重放时非空
 */
public record ItemIdempotencyClaim<T>(
        Status status,
        T replay) {

    /**
     * 幂等占位状态。
     */
    public enum Status {
        NEW,
        REPLAY,
        CONFLICT
    }
}
