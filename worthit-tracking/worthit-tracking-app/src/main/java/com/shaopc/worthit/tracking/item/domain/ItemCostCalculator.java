package com.shaopc.worthit.tracking.item.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 统一计算物品计划日均与持有日均。
 */
public final class ItemCostCalculator {

    private static final BigDecimal DAYS_PER_YEAR =
            BigDecimal.valueOf(365);
    private static final BigDecimal ONE_CENT =
            new BigDecimal("0.01");
    private static final MathContext CALCULATION_CONTEXT =
            MathContext.DECIMAL128;

    private ItemCostCalculator() {
    }

    /**
     * 按冻结公式派生物品成本。
     *
     * @param purchasePrice 购买价格
     * @param expectedYears 预计使用年限
     * @param residualValue 预计残值；空表示未计残值
     * @param purchaseDate 购买日期
     * @param today 当前业务日期
     * @return 成本派生结果
     */
    public static ItemCost calculate(
            BigDecimal purchasePrice,
            BigDecimal expectedYears,
            BigDecimal residualValue,
            LocalDate purchaseDate,
            LocalDate today) {
        int expectedUseDays = expectedUseDays(expectedYears);
        BigDecimal exactPlan = calculateExactPlanDailyCost(
                purchasePrice, expectedYears, residualValue);
        BigDecimal roundedPlan = rounded(exactPlan);

        Integer holdingDays = null;
        BigDecimal exactHolding = null;
        BigDecimal roundedHolding = null;
        String holdingDisplay = null;
        if (purchaseDate != null) {
            holdingDays = Math.toIntExact(
                    ChronoUnit.DAYS.between(
                            purchaseDate, today) + 1);
            exactHolding = purchasePrice.divide(
                    BigDecimal.valueOf(holdingDays),
                    CALCULATION_CONTEXT);
            roundedHolding = rounded(exactHolding);
            holdingDisplay = display(
                    exactHolding, roundedHolding);
        }

        return new ItemCost(
                expectedUseDays,
                exactPlan,
                roundedPlan,
                display(exactPlan, roundedPlan),
                isTiny(exactPlan),
                residualValue == null,
                holdingDays,
                exactHolding,
                roundedHolding,
                holdingDisplay);
    }

    /**
     * 计算未舍入的计划日均，供汇总场景先累加精确值。
     *
     * @param purchasePrice 购买价格
     * @param expectedYears 预计使用年限
     * @param residualValue 预计残值；空时按零参与计算
     * @return 未舍入的计划日均
     */
    public static BigDecimal calculateExactPlanDailyCost(
            BigDecimal purchasePrice,
            BigDecimal expectedYears,
            BigDecimal residualValue) {
        BigDecimal countedResidual = residualValue == null
                ? BigDecimal.ZERO
                : residualValue;
        return purchasePrice
                .subtract(countedResidual)
                .max(BigDecimal.ZERO)
                .divide(
                        BigDecimal.valueOf(
                                expectedUseDays(expectedYears)),
                        CALCULATION_CONTEXT);
    }

    private static int expectedUseDays(
            BigDecimal expectedYears) {
        return expectedYears
                .multiply(DAYS_PER_YEAR)
                .setScale(0, RoundingMode.CEILING)
                .intValueExact();
    }

    private static BigDecimal rounded(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static String display(
            BigDecimal exact, BigDecimal rounded) {
        if (isTiny(exact)) {
            return "<¥0.01/天";
        }
        return "¥" + rounded.toPlainString() + "/天";
    }

    private static boolean isTiny(BigDecimal exact) {
        return exact.signum() > 0
                && exact.compareTo(ONE_CENT) < 0;
    }
}
