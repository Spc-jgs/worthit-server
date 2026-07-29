package com.shaopc.worthit.tracking.subscription.interfaces.rest;

import com.shaopc.worthit.tracking.interfaces.rest.PositiveLongIdParser;
import com.shaopc.worthit.tracking.subscription.domain.AutoRenew;
import com.shaopc.worthit.tracking.subscription.domain.BillingCycleType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * 新建订阅公网请求。
 */
@Schema(description = "新建订阅请求")
public record CreateSubscriptionRequest(
        @NotBlank(message = "订阅名称不能为空")
        @Size(max = 64, message = "订阅名称不能超过64个字符")
        String name,
        @Pattern(
                regexp = PositiveLongIdParser.PATTERN,
                message = "分类标识格式不正确")
        String categoryId,
        @NotBlank(message = "周期金额不能为空")
        @DecimalMin(value = "0", message = "周期金额不能小于0")
        @Digits(
                integer = 12,
                fraction = 6,
                message = "周期金额最多12位整数和6位小数")
        String amount,
        @NotBlank(message = "币种不能为空")
        @Pattern(
                regexp = "[A-Za-z]{3}",
                message = "币种必须是3位字母")
        String currency,
        @NotNull(message = "计费周期不能为空")
        BillingCycleType billingCycleType,
        @Positive(message = "计费周期参数必须大于0")
        Integer billingCycleValue,
        @DecimalMin(
                value = "0",
                message = "人民币参考金额不能小于0")
        @Digits(
                integer = 12,
                fraction = 6,
                message = "人民币参考金额最多12位整数和6位小数")
        String cnyReferenceAmount,
        LocalDate nextRenewalDate,
        AutoRenew autoRenew,
        Boolean renewalReminderEnabled,
        @Size(max = 512, message = "备注不能超过512个字符")
        String remark) {
}
