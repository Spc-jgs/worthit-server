package com.shaopc.worthit.tracking.item.application;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 更新物品命令。
 *
 * @param version 客户端读取到的乐观锁版本
 * @param name 物品名称
 * @param categoryId 分类标识
 * @param purchasePrice 购买价格
 * @param expectedYears 预计使用年限
 * @param residualValue 预计残值，空值表示未计残值
 * @param purchaseDate 购买日期
 * @param warrantyExpireDate 保修到期日
 * @param warrantyReminderEnabled 是否开启保修提醒
 * @param brandModel 品牌型号
 * @param remark 备注
 */
public record UpdateItemCommand(
        long version,
        String name,
        Long categoryId,
        BigDecimal purchasePrice,
        BigDecimal expectedYears,
        BigDecimal residualValue,
        LocalDate purchaseDate,
        LocalDate warrantyExpireDate,
        boolean warrantyReminderEnabled,
        String brandModel,
        String remark) {
}
