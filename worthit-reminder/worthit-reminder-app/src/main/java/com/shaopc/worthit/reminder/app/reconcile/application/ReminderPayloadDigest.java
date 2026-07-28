package com.shaopc.worthit.reminder.app.reconcile.application;

import com.shaopc.worthit.reminder.client.command.ReconcileReminderCommand;

/**
 * reconcile 冻结字段的规范化摘要边界。
 */
@FunctionalInterface
public interface ReminderPayloadDigest {

    /**
     * 生成不依赖 JSON 键顺序的 SHA-256 摘要。
     */
    String hash(ReconcileReminderCommand command);
}
