package com.shaopc.worthit.tracking.lifecycle.infrastructure.persistence;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Disposal 与 Replacement 联合查询行。
 */
@Getter
@Setter
@NoArgsConstructor
public class LifecycleReviewRow {

    private Long id;
    private String entryType;
    private LocalDate eventDate;
    private LocalDateTime createTime;
    private Long itemId;
    private String itemName;
    private String disposalType;
    private LocalDate disposalDate;
    private BigDecimal saleAmount;
    private BigDecimal purchasePriceSnapshot;
    private Long oldItemId;
    private String oldItemName;
    private Long newItemId;
    private String newItemName;
}
