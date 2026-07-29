package com.shaopc.worthit.reminder.app.reconcile.application;

import com.shaopc.worthit.reminder.client.command.ReconcileReminderCommand;
import com.shaopc.worthit.reminder.client.response.ReconcileReminderResponse;

/**
 * Reminder 内部协调公开应用用例。
 */
public interface ReminderReconcileService {

    /**
     * 按 Binding、来源版本和载荷摘要协调提醒实例。
     *
     * @param eventId 幂等事件标识
     * @param command 协调命令
     * @return 协调结果
     */
    ReconcileReminderResponse reconcile(
            String eventId,
            ReconcileReminderCommand command);
}
