package com.shaopc.worthit.tracking.item.application;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Item 详情用例结果。
 */
public record ItemDetail(
        long id,
        String name,
        long categoryId,
        String categoryName,
        String purchasePrice,
        String expectedYears,
        String residualValue,
        boolean residualUnset,
        LocalDate purchaseDate,
        LocalDate warrantyExpireDate,
        boolean warrantyReminderEnabled,
        String brandModel,
        String remark,
        String lifecycleStatus,
        int expectedUseDays,
        String planDailyCost,
        String planDailyCostDisplay,
        boolean planDailyCostTiny,
        Integer holdingDays,
        String holdingDailyCost,
        String holdingDailyCostDisplay,
        long version,
        LocalDateTime createTime,
        LocalDateTime updateTime) {
}
