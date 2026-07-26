package com.shaopc.worthit.tracking.item.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 物品聚合事实。
 */
public record Item(
        long id,
        long userId,
        long categoryId,
        String name,
        BigDecimal purchasePrice,
        BigDecimal expectedYears,
        BigDecimal residualValue,
        LocalDate purchaseDate,
        LocalDate warrantyExpireDate,
        boolean warrantyReminderEnabled,
        String brandModel,
        String remark,
        String lifecycleStatus,
        long version,
        LocalDateTime createTime,
        LocalDateTime updateTime) {

    /** M1 新建物品的固定生命周期状态。 */
    public static final String HOLDING = "HOLDING";
}
