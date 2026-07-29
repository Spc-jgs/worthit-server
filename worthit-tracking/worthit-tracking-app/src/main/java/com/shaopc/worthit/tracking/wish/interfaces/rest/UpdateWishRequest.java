package com.shaopc.worthit.tracking.wish.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** 更新想买公网请求。 */
@Schema(description = "更新想买请求")
public record UpdateWishRequest(
        @Positive(message = "版本必须大于0")
        long version,
        @NotBlank(message = "想买名称不能为空")
        @Size(max = 64, message = "想买名称不能超过64个字符")
        String name,
        @Pattern(
                regexp = "[1-9]\\d{0,18}",
                message = "分类标识格式不正确")
        String categoryId,
        @NotBlank(message = "预计价格不能为空")
        @DecimalMin(value = "0", message = "预计价格不能小于0")
        @Digits(integer = 12, fraction = 6,
                message = "预计价格最多12位整数和6位小数")
        String expectedPrice,
        @NotBlank(message = "预计使用年限不能为空")
        @DecimalMin(value = "0.001",
                message = "预计使用年限必须大于0")
        @Digits(integer = 5, fraction = 3,
                message = "预计使用年限最多5位整数和3位小数")
        String expectedYears,
        @DecimalMin(value = "0", message = "预计残值不能小于0")
        @Digits(integer = 12, fraction = 6,
                message = "预计残值最多12位整数和6位小数")
        String residualValue,
        @Size(max = 512, message = "想买原因不能超过512个字符")
        String reason,
        @Size(max = 512, message = "备注不能超过512个字符")
        String remark,
        LocalDate watchDeadline,
        @NotNull(message = "观望提醒开关不能为空")
        Boolean watchReminderEnabled) {
}
