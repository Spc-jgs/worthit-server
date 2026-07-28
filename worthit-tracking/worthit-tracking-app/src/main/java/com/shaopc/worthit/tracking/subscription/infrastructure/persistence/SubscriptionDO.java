package com.shaopc.worthit.tracking.subscription.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * {@code trk_subscription} 持久化对象。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("trk_subscription")
public class SubscriptionDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private Long categoryId;
    private String name;
    private BigDecimal amount;
    private String currency;
    private String billingCycleType;
    private Integer billingCycleValue;
    private BigDecimal cnyReferenceAmount;
    private LocalDate nextRenewalDate;
    private String autoRenew;
    private Boolean renewalReminderEnabled;
    private String status;
    private String remark;
    private Long version;
    private Long createBy;
    private LocalDateTime createTime;
    private Long updateBy;
    private LocalDateTime updateTime;
    private Boolean delFlag;
    private LocalDateTime deleteTime;
}
