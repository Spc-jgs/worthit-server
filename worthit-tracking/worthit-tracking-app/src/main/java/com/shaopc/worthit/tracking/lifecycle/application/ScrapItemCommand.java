package com.shaopc.worthit.tracking.lifecycle.application;

import java.time.LocalDate;

/**
 * 报废用例命令。
 */
public record ScrapItemCommand(
        long version,
        LocalDate scrapDate,
        String remark) {
}
