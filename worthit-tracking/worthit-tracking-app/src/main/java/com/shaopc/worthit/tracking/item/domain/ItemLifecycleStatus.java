package com.shaopc.worthit.tracking.item.domain;

/**
 * 物品生命周期状态。
 */
public enum ItemLifecycleStatus {

    /** 持有中。 */
    HOLDING("HOLDING");

    private final String code;

    ItemLifecycleStatus(String code) {
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
    public static ItemLifecycleStatus fromCode(String code) {
        for (ItemLifecycleStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException(
                "不支持的物品生命周期状态: " + code);
    }
}
