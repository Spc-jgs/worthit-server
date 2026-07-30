package com.shaopc.worthit.tracking.item.interfaces.rest;

import com.shaopc.worthit.tracking.lifecycle.interfaces.rest.ItemDisposalResponse;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 物品详情公网响应。
 */
public record ItemDetailResponse(
        String id,
        String name,
        String categoryId,
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
        ItemDisposalResponse disposal,
        long version,
        LocalDateTime createTime,
        LocalDateTime updateTime) {
}
