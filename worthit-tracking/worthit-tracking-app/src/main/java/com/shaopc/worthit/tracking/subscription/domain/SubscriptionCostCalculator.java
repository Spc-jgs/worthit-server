package com.shaopc.worthit.tracking.subscription.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * 订阅标准化月成本领域计算器。
 */
public final class SubscriptionCostCalculator {

    private static final MathContext CALCULATION_CONTEXT =
            new MathContext(24, RoundingMode.HALF_UP);
    private static final BigDecimal MONTHS_PER_YEAR =
            BigDecimal.valueOf(12);
    private static final BigDecimal DAYS_PER_YEAR =
            BigDecimal.valueOf(365);

    private SubscriptionCostCalculator() {
    }

    /**
     * 按计费周期计算原币与人民币参考月成本。
     */
    public static SubscriptionMonthlyCost calculate(
            BigDecimal amount,
            String currency,
            BillingCycleType cycleType,
            Integer cycleValue,
            BigDecimal cnyReferenceAmount) {
        BigDecimal original = normalizeMonthly(
                amount, cycleType, cycleValue);
        boolean cny = "CNY".equals(currency);
        boolean include = cny || cnyReferenceAmount != null;
        boolean approximate = !cny && cnyReferenceAmount != null;
        BigDecimal cnyMonthly = cny
                ? original
                : cnyReferenceAmount == null
                ? null
                : normalizeMonthly(
                        cnyReferenceAmount,
                        cycleType,
                        cycleValue);
        return new SubscriptionMonthlyCost(
                displayValue(original),
                cny
                        ? "¥" + money(original) + "/月"
                        : money(original) + " "
                        + currency + "/月",
                cnyMonthly == null
                        ? null
                        : displayValue(cnyMonthly),
                cnyMonthly == null
                        ? null
                        : (approximate ? "约 " : "")
                        + "¥" + money(cnyMonthly) + "/月",
                approximate,
                include);
    }

    /**
     * 返回未舍入的高精度标准月成本，供 Dashboard 先汇总后舍入。
     */
    public static BigDecimal normalizeMonthly(
            BigDecimal amount,
            BillingCycleType cycleType,
            Integer cycleValue) {
        return switch (cycleType) {
            case MONTHLY -> amount;
            case YEARLY -> amount.divide(
                    MONTHS_PER_YEAR, CALCULATION_CONTEXT);
            case MULTI_MONTH -> amount.divide(
                    BigDecimal.valueOf(cycleValue),
                    CALCULATION_CONTEXT);
            case FIXED_DAYS -> amount
                    .divide(
                            BigDecimal.valueOf(cycleValue),
                            CALCULATION_CONTEXT)
                    .multiply(
                            DAYS_PER_YEAR,
                            CALCULATION_CONTEXT)
                    .divide(
                            MONTHS_PER_YEAR,
                            CALCULATION_CONTEXT);
        };
    }

    private static BigDecimal displayValue(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static String money(BigDecimal value) {
        return displayValue(value).toPlainString();
    }
}
