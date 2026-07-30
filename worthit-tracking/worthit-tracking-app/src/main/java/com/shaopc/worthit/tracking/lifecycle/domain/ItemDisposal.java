package com.shaopc.worthit.tracking.lifecycle.domain;

import com.shaopc.worthit.common.core.error.BusinessException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 不可变的物品处置事实。
 */
public record ItemDisposal(
        long id,
        long userId,
        long itemId,
        DisposalType type,
        LocalDate disposalDate,
        BigDecimal purchasePriceSnapshot,
        BigDecimal saleAmount,
        String remark,
        LocalDateTime createTime,
        LocalDateTime updateTime) {

    private static final int MONEY_PRECISION = 18;
    private static final int MONEY_SCALE = 6;
    private static final int MAX_REMARK_LENGTH = 512;

    /**
     * 校验处置事实自身的持久化不变量。
     */
    public ItemDisposal {
        if (id <= 0 || userId <= 0 || itemId <= 0) {
            throw invalid();
        }
        Objects.requireNonNull(type, "处置类型不能为空");
        Objects.requireNonNull(
                disposalDate, "处置日期不能为空");
        Objects.requireNonNull(
                createTime, "处置创建时间不能为空");
        Objects.requireNonNull(
                updateTime, "处置更新时间不能为空");
        requireMoney(purchasePriceSnapshot);
        if (purchasePriceSnapshot.signum() < 0) {
            throw invalid();
        }
        validateSaleAmount(type, saleAmount);
        if (remark != null
                && remark.length() > MAX_REMARK_LENGTH) {
            throw invalid();
        }
    }

    /**
     * 返回卖出净成本；非卖出处置没有该派生值。
     */
    public BigDecimal netCost() {
        if (type != DisposalType.SOLD) {
            return null;
        }
        return purchasePriceSnapshot.subtract(saleAmount);
    }

    private static void validateSaleAmount(
            DisposalType type,
            BigDecimal saleAmount) {
        if (type == DisposalType.SOLD) {
            requireMoney(saleAmount);
            if (saleAmount.signum() < 0) {
                throw invalid();
            }
            return;
        }
        if (saleAmount != null) {
            throw invalid();
        }
    }

    private static void requireMoney(BigDecimal value) {
        if (value == null
                || value.precision() > MONEY_PRECISION
                || value.scale() > MONEY_SCALE) {
            throw invalid();
        }
    }

    private static BusinessException invalid() {
        return new BusinessException(
                LifecycleErrorCode.VAL_INVALID_ARGUMENT);
    }
}
