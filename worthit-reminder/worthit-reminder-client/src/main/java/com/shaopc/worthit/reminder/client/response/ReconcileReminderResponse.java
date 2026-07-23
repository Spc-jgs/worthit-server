package com.shaopc.worthit.reminder.client.response;

import com.shaopc.worthit.reminder.client.model.ReconcileResultCode;

/**
 * Reminder reconcile 处理结果。
 *
 * @param applied 是否应用了命令期望状态
 * @param resultCode reconcile 稳定结果码
 * @param idempotent 是否为相同事件的幂等重放
 * @param bindingId Reminder Binding 标识
 * @param lastSourceVersion Binding 已接受的最新来源版本
 */
public record ReconcileReminderResponse(
        boolean applied,
        ReconcileResultCode resultCode,
        boolean idempotent,
        long bindingId,
        long lastSourceVersion) {
}
