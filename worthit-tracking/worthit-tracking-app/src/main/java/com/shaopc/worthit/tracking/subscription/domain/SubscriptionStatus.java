package com.shaopc.worthit.tracking.subscription.domain;

/**
 * 订阅状态。
 */
public enum SubscriptionStatus {

    /** 生效中。 */
    ACTIVE("ACTIVE"),
    /** 已暂停。 */
    PAUSED("PAUSED"),
    /** 已结束。 */
    ENDED("ENDED");

    private final String code;

    SubscriptionStatus(String code) {
        this.code = code;
    }

    /**
     * 返回持久化与公网契约使用的稳定编码。
     */
    public String code() {
        return code;
    }

    /**
     * 从持久化编码恢复状态。
     *
     * @throws IllegalArgumentException 编码为空或不受支持
     */
    public static SubscriptionStatus fromCode(String code) {
        for (SubscriptionStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException(
                "不支持的订阅状态: " + code);
    }
}
