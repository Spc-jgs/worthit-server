package com.shaopc.worthit.tracking.wish.infrastructure.persistence;

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
 * {@code trk_wish} 持久化对象。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("trk_wish")
public class WishDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private Long categoryId;
    private String name;
    private BigDecimal expectedPrice;
    private BigDecimal expectedYears;
    private BigDecimal residualValue;
    private String reason;
    private String remark;
    private LocalDate watchDeadline;
    private Boolean watchReminderEnabled;
    private String status;
    private String lastAbandonReason;
    private LocalDateTime lastAbandonAt;
    private Long convertedItemId;
    private String conversionKey;
    private Long version;
    private Long createBy;
    private LocalDateTime createTime;
    private Long updateBy;
    private LocalDateTime updateTime;
    private Boolean delFlag;
    private LocalDateTime deleteTime;
}
