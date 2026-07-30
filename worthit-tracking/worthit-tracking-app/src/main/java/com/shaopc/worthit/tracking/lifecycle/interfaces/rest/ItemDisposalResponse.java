package com.shaopc.worthit.tracking.lifecycle.interfaces.rest;

import java.time.LocalDate;

/**
 * 公网处置事实响应。
 */
public record ItemDisposalResponse(
        String type,
        LocalDate date,
        String saleAmount,
        String remark,
        String netCost) {
}
