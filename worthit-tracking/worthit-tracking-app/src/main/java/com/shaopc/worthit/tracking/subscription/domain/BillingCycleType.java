package com.shaopc.worthit.tracking.subscription.domain;

/**
 * 订阅计费周期类型。
 */
public enum BillingCycleType {

    /** 每月。 */
    MONTHLY("MONTHLY"),
    /** 每年。 */
    YEARLY("YEARLY"),
    /** 固定月数。 */
    MULTI_MONTH("MULTI_MONTH"),
    /** 固定天数。 */
    FIXED_DAYS("FIXED_DAYS");

    private final String code;

    BillingCycleType(String code) {
        this.code = code;
    }

    /**
     * 返回持久化与公网契约使用的稳定编码。
     */
    public String code() {
        return code;
    }

    /**
     * 从持久化编码恢复周期类型。
     */
    public static BillingCycleType fromCode(String code) {
        for (BillingCycleType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException(
                "不支持的计费周期类型: " + code);
    }
}
