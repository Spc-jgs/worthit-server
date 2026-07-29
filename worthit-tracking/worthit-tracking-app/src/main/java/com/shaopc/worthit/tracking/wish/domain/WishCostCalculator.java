package com.shaopc.worthit.tracking.wish.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * 按冻结公式计算想买计划日均。
 */
public final class WishCostCalculator {

    private static final BigDecimal DAYS_PER_YEAR =
            BigDecimal.valueOf(365);
    private static final BigDecimal ONE_CENT =
            new BigDecimal("0.01");

    private WishCostCalculator() {
    }

    /**
     * 计算想买计划日均，残值为空时按零计算但保留未计残值语义。
     */
    public static WishCost calculate(
            BigDecimal expectedPrice,
            BigDecimal expectedYears,
            BigDecimal residualValue) {
        int days = expectedYears.multiply(DAYS_PER_YEAR)
                .setScale(0, RoundingMode.CEILING)
                .intValueExact();
        BigDecimal exact = expectedPrice
                .subtract(residualValue == null
                        ? BigDecimal.ZERO : residualValue)
                .max(BigDecimal.ZERO)
                .divide(
                        BigDecimal.valueOf(days),
                        MathContext.DECIMAL128);
        BigDecimal rounded =
                exact.setScale(2, RoundingMode.HALF_UP);
        boolean tiny = exact.signum() > 0
                && exact.compareTo(ONE_CENT) < 0;
        return new WishCost(
                days,
                exact,
                rounded,
                tiny ? "<¥0.01/天"
                        : "¥" + rounded.toPlainString() + "/天",
                tiny,
                residualValue == null);
    }
}
