package com.shaopc.worthit.tracking.wish.domain;

/**
 * 想买状态。
 */
public enum WishStatus {

    /** 正在考虑。 */
    CONSIDERING("CONSIDERING"),
    /** 已购买并转换为物品。 */
    PURCHASED("PURCHASED"),
    /** 已放弃。 */
    ABANDONED("ABANDONED");

    private final String code;

    WishStatus(String code) {
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
    public static WishStatus fromCode(String code) {
        for (WishStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException(
                "不支持的想买状态: " + code);
    }
}
