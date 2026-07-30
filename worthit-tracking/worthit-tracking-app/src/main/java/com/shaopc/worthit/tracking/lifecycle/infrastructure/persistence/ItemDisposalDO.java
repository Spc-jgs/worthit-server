package com.shaopc.worthit.tracking.lifecycle.infrastructure.persistence;

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
 * {@code trk_item_disposal} 持久化对象。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("trk_item_disposal")
public class ItemDisposalDO {

    @TableId(type = IdType.INPUT)
    private Long id;
    private Long userId;
    private Long itemId;
    private String disposalType;
    private LocalDate disposalDate;
    private BigDecimal purchasePriceSnapshot;
    private BigDecimal saleAmount;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
