package com.shaopc.worthit.tracking.item.application;

import com.shaopc.worthit.reminder.client.command.ReconcileReminderCommand;

/**
 * Item 与 Reminder 协作的 Outbox 写边界。
 */
@FunctionalInterface
public interface ItemOutboxWriter {

    /**
     * 写入待投递的完整提醒期望。
     *
     * @param command Reminder reconcile 命令
     */
    void write(ReconcileReminderCommand command);
}
