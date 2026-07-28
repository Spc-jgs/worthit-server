package com.shaopc.worthit.reminder.app.reminder.application;

import com.shaopc.worthit.reminder.client.model.ReminderBusinessType;
import com.shaopc.worthit.reminder.client.model.ReminderType;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 提醒中心列表项。
 */
public record ReminderListItem(
        long id,
        ReminderType reminderType,
        ReminderBusinessType businessType,
        long businessId,
        LocalDate businessDate,
        LocalDateTime remindAt,
        String status) {
}
