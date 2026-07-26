package com.shaopc.worthit.tracking.item.application;

/**
 * Item 新建幂等占位结果。
 *
 * @param status 占位状态
 * @param replay 首次调用结果；仅重放时非空
 */
public record ItemIdempotencyClaim(
        Status status,
        ItemDetail replay) {

    /**
     * 幂等占位状态。
     */
    public enum Status {
        NEW,
        REPLAY,
        CONFLICT
    }
}
