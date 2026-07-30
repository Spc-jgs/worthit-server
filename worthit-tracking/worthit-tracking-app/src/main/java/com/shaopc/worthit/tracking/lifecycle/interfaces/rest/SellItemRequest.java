package com.shaopc.worthit.tracking.lifecycle.interfaces.rest;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * 卖出公网请求。
 */
public record SellItemRequest(
        @Min(value = 1, message = "版本必须为正整数")
        long version,
        @NotNull(message = "卖出日期不能为空")
        LocalDate saleDate,
        @NotBlank(message = "卖出金额不能为空")
        @Pattern(
                regexp = "(?:0|[1-9]\\d{0,11})(?:\\.\\d{1,6})?",
                message = "卖出金额格式不合法")
        String saleAmount,
        @Size(max = 512, message = "备注最多512个字符")
        String remark) {
}
