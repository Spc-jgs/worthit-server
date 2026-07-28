package com.shaopc.worthit.reminder.app.reminder.interfaces.rest;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 提醒中心列表项响应。
 */
public record ReminderItemResponse(
        String id,
        String reminderType,
        String businessType,
        String businessId,
        String businessName,
        LocalDate businessDate,
        LocalDateTime remindAt,
        String status,
        String title,
        String detailPath) {
}
