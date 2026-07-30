package com.shaopc.worthit.tracking.lifecycle.domain;

import com.shaopc.worthit.tracking.item.domain.ItemLifecycleStatus;

/**
 * 物品处置类型。
 */
public enum DisposalType {

    /** 退货。 */
    RETURNED("RETURNED", ItemLifecycleStatus.RETURNED),
    /** 卖出。 */
    SOLD("SOLD", ItemLifecycleStatus.SOLD),
    /** 报废。 */
    SCRAPPED("SCRAPPED", ItemLifecycleStatus.SCRAPPED);

    private final String code;
    private final ItemLifecycleStatus targetStatus;

    DisposalType(
            String code,
            ItemLifecycleStatus targetStatus) {
        this.code = code;
        this.targetStatus = targetStatus;
    }

    /**
     * 返回持久化与公网契约使用的稳定编码。
     */
    public String code() {
        return code;
    }

    /**
     * 返回处置完成后的 Item 生命周期终态。
     */
    public ItemLifecycleStatus targetStatus() {
        return targetStatus;
    }

    /**
     * 从持久化编码恢复处置类型。
     */
    public static DisposalType fromCode(String code) {
        for (DisposalType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException(
                "不支持的物品处置类型: " + code);
    }
}
