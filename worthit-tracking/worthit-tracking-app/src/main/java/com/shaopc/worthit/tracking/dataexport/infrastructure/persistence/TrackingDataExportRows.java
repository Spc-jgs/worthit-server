package com.shaopc.worthit.tracking.dataexport.infrastructure.persistence;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Tracking 导出专用只读行模型；仅包含允许出站的业务字段。
 */
public final class TrackingDataExportRows {

    private TrackingDataExportRows() {
    }

    /** 分类行。 */
    @Getter
    @Setter
    public static class CategoryRow {
        private Long id;
        private String name;
        private String systemCode;
        private Long version;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
        private Integer delFlag;
        private LocalDateTime deleteTime;
    }

    /** 物品行。 */
    @Getter
    @Setter
    public static class ItemRow {
        private Long id;
        private Long categoryId;
        private String name;
        private BigDecimal purchasePrice;
        private BigDecimal expectedYears;
        private BigDecimal residualValue;
        private LocalDate purchaseDate;
        private LocalDate warrantyExpireDate;
        private Integer warrantyReminderEnabled;
        private String brandModel;
        private String remark;
        private Long sourceWishId;
        private String lifecycleStatus;
        private Long version;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
        private Integer delFlag;
        private LocalDateTime deleteTime;
    }

    /** 订阅行。 */
    @Getter
    @Setter
    public static class SubscriptionRow {
        private Long id;
        private Long categoryId;
        private String name;
        private BigDecimal amount;
        private String currency;
        private String billingCycleType;
        private Integer billingCycleValue;
        private BigDecimal cnyReferenceAmount;
        private LocalDate nextRenewalDate;
        private String autoRenew;
        private Integer renewalReminderEnabled;
        private String status;
        private String remark;
        private Long version;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
        private Integer delFlag;
        private LocalDateTime deleteTime;
    }

    /** 想买行。 */
    @Getter
    @Setter
    public static class WishRow {
        private Long id;
        private Long categoryId;
        private String name;
        private BigDecimal expectedPrice;
        private BigDecimal expectedYears;
        private BigDecimal residualValue;
        private String reason;
        private String remark;
        private LocalDate watchDeadline;
        private Integer watchReminderEnabled;
        private String status;
        private String lastAbandonReason;
        private LocalDateTime lastAbandonAt;
        private Long convertedItemId;
        private Long version;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
        private Integer delFlag;
        private LocalDateTime deleteTime;
    }

    /** 处置行。 */
    @Getter
    @Setter
    public static class DisposalRow {
        private Long id;
        private Long itemId;
        private String disposalType;
        private LocalDate disposalDate;
        private BigDecimal purchasePriceSnapshot;
        private BigDecimal saleAmount;
        private String remark;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    /** 替换关系行。 */
    @Getter
    @Setter
    public static class ReplacementRow {
        private Long id;
        private Long oldItemId;
        private Long newItemId;
        private LocalDateTime createTime;
    }
}
