package com.shaopc.worthit.tracking.subscription.domain;

/**
 * 自动续费认知状态。
 */
public enum AutoRenew {

    /** 会自动续费。 */
    YES("YES"),
    /** 不会自动续费。 */
    NO("NO"),
    /** 用户尚未确认。 */
    UNKNOWN("UNKNOWN");

    private final String code;

    AutoRenew(String code) {
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
     */
    public static AutoRenew fromCode(String code) {
        for (AutoRenew value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        throw new IllegalArgumentException(
                "不支持的自动续费状态: " + code);
    }
}
