package com.shaopc.worthit.tracking.outbox.application;

import com.shaopc.worthit.reminder.client.command.ReconcileReminderCommand;

/**
 * Tracking 与 Reminder 协作的 Outbox 写边界。
 */
@FunctionalInterface
public interface ReminderOutboxWriter {

    /**
     * 写入待投递的完整提醒期望。
     */
    void write(ReconcileReminderCommand command);
}
