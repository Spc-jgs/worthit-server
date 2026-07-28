package com.shaopc.worthit.reminder.app.reconcile.application;

import java.time.LocalDateTime;

/**
 * Binding 当前唯一 PENDING 实例。
 *
 * @param id 实例标识
 * @param remindAt 到期可见时刻
 */
public record ReminderPendingInstance(
        long id,
        LocalDateTime remindAt) {
}
