package com.shaopc.worthit.tracking.outbox.application;

/**
 * Tracking Outbox 投递状态。
 */
public enum OutboxStatus {

    /** 等待首次投递。 */
    NEW("NEW"),
    /** 已被 Relay 租约持有者抢占。 */
    PROCESSING("PROCESSING"),
    /** 等待下次重试。 */
    RETRY_WAIT("RETRY_WAIT"),
    /** 投递成功。 */
    SUCCEEDED("SUCCEEDED"),
    /** 超过最大重试次数。 */
    DEAD("DEAD");

    private final String code;

    OutboxStatus(String code) {
        this.code = code;
    }

    /**
     * 返回持久化使用的稳定状态编码。
     */
    public String code() {
        return code;
    }
}
