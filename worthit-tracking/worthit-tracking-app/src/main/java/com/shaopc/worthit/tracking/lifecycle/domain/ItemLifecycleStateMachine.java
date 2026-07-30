package com.shaopc.worthit.tracking.lifecycle.domain;

import com.shaopc.worthit.common.core.error.BusinessException;
import com.shaopc.worthit.tracking.item.domain.Item;
import com.shaopc.worthit.tracking.item.domain.ItemErrorCode;
import com.shaopc.worthit.tracking.item.domain.ItemLifecycleStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 物品从持有中单向进入处置终态的领域状态机。
 */
public final class ItemLifecycleStateMachine {

    private ItemLifecycleStateMachine() {
    }

    /**
     * 校验状态转换与上下文不变量，创建不可变处置事实。
     *
     * @param disposalId 新处置事实 ID
     * @param item 当前物品聚合
     * @param type 处置类型
     * @param disposalDate 处置日期
     * @param saleAmount 卖出金额；非卖出必须为空
     * @param remark 处置备注
     * @param today 服务端当前业务日期
     * @param now 服务端当前时间
     * @return 处置事实
     */
    public static ItemDisposal dispose(
            long disposalId,
            Item item,
            DisposalType type,
            LocalDate disposalDate,
            BigDecimal saleAmount,
            String remark,
            LocalDate today,
            LocalDateTime now) {
        Item requiredItem =
                Objects.requireNonNull(item, "物品不能为空");
        DisposalType requiredType =
                Objects.requireNonNull(type, "处置类型不能为空");
        if (requiredItem.lifecycleStatus()
                != ItemLifecycleStatus.HOLDING) {
            throw new BusinessException(
                    ItemErrorCode.VAL_STATE_CONFLICT);
        }
        if (disposalDate == null
                || today == null
                || now == null
                || disposalDate.isAfter(today)
                || requiredItem.purchaseDate() != null
                && disposalDate.isBefore(
                        requiredItem.purchaseDate())) {
            throw new BusinessException(
                    LifecycleErrorCode.VAL_INVALID_ARGUMENT);
        }
        return new ItemDisposal(
                disposalId,
                requiredItem.userId(),
                requiredItem.id(),
                requiredType,
                disposalDate,
                requiredItem.purchasePrice(),
                saleAmount,
                normalizeRemark(remark),
                now,
                now);
    }

    private static String normalizeRemark(String remark) {
        if (remark == null) {
            return null;
        }
        String normalized = remark.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
