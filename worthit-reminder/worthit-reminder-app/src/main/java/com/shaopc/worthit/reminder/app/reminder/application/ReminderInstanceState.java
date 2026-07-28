package com.shaopc.worthit.reminder.app.reminder.application;

import java.time.LocalDateTime;

/**
 * 忽略用例锁定后的提醒实例状态。
 */
public record ReminderInstanceState(
        long id,
        String status,
        LocalDateTime remindAt) {
}
