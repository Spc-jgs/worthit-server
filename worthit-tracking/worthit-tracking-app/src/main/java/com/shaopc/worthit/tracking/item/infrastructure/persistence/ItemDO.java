package com.shaopc.worthit.tracking.item.infrastructure.persistence;

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
 * {@code trk_item} 持久化对象。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("trk_item")
public class ItemDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private Long categoryId;
    private String name;
    private BigDecimal purchasePrice;
    private BigDecimal expectedYears;
    private BigDecimal residualValue;
    private LocalDate purchaseDate;
    private LocalDate warrantyExpireDate;
    private Boolean warrantyReminderEnabled;
    private String brandModel;
    private String remark;
    private Long sourceWishId;
    private String lifecycleStatus;
    private Long version;
    private Long createBy;
    private LocalDateTime createTime;
    private Long updateBy;
    private LocalDateTime updateTime;
    private Boolean delFlag;
    private LocalDateTime deleteTime;
}
