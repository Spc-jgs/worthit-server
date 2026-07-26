package com.shaopc.worthit.tracking.item.infrastructure.persistence;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Item 与分类名称的数据库查询投影。
 */
@Getter
@Setter
@NoArgsConstructor
public class ItemViewDO {

    private Long id;
    private Long userId;
    private Long categoryId;
    private String categoryName;
    private String name;
    private BigDecimal purchasePrice;
    private BigDecimal expectedYears;
    private BigDecimal residualValue;
    private LocalDate purchaseDate;
    private LocalDate warrantyExpireDate;
    private Boolean warrantyReminderEnabled;
    private String brandModel;
    private String remark;
    private String lifecycleStatus;
    private Long version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
