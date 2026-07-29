package com.shaopc.worthit.tracking.idempotency.application;

/**
 * Tracking 写接口幂等操作。
 */
public enum TrackingOperation {

    /** 创建物品。 */
    ITEM_CREATE("ITEM_CREATE"),
    /** 更新物品。 */
    ITEM_UPDATE("ITEM_UPDATE"),
    /** 删除物品。 */
    ITEM_DELETE("ITEM_DELETE"),
    /** 恢复物品。 */
    ITEM_RESTORE("ITEM_RESTORE"),
    /** 创建订阅。 */
    SUB_CREATE("SUB_CREATE"),
    /** 更新订阅。 */
    SUB_UPDATE("SUB_UPDATE"),
    /** 暂停订阅。 */
    SUB_PAUSE("SUB_PAUSE"),
    /** 结束订阅。 */
    SUB_END("SUB_END"),
    /** 恢复已暂停订阅。 */
    SUB_RESUME("SUB_RESUME"),
    /** 删除订阅。 */
    SUB_DELETE("SUB_DELETE"),
    /** 恢复已删除订阅。 */
    SUB_RESTORE("SUB_RESTORE"),
    /** 创建想买。 */
    WISH_CREATE("WISH_CREATE"),
    /** 更新想买。 */
    WISH_UPDATE("WISH_UPDATE"),
    /** 购买想买。 */
    WISH_PURCHASE("WISH_PURCHASE"),
    /** 放弃想买。 */
    WISH_ABANDON("WISH_ABANDON"),
    /** 重新考虑想买。 */
    WISH_RECONSIDER("WISH_RECONSIDER"),
    /** 删除想买。 */
    WISH_DELETE("WISH_DELETE"),
    /** 恢复已删除想买。 */
    WISH_RESTORE("WISH_RESTORE");

    private final String code;

    TrackingOperation(String code) {
        this.code = code;
    }

    /**
     * 返回持久化使用的稳定操作编码。
     */
    public String code() {
        return code;
    }
}
