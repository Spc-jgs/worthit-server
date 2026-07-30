package com.shaopc.worthit.tracking.lifecycle.interfaces.rest;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * 退货公网请求。
 */
public record ReturnItemRequest(
        @Min(value = 1, message = "版本必须为正整数")
        long version,
        @NotNull(message = "退货日期不能为空")
        LocalDate returnDate,
        @Size(max = 512, message = "备注最多512个字符")
        String remark) {
}
