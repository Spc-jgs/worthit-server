package com.shaopc.worthit.tracking.lifecycle.application;

import java.time.LocalDate;

/**
 * 退货用例命令。
 */
public record ReturnItemCommand(
        long version,
        LocalDate returnDate,
        String remark) {
}
