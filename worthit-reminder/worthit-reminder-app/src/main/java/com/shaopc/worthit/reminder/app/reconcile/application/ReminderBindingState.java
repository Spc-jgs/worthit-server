package com.shaopc.worthit.reminder.app.reconcile.application;

/**
 * 已锁定的 Reminder Binding 当前状态。
 *
 * @param id Binding 标识
 * @param reminderEnabled 当前提醒期望开关
 * @param lastSourceVersion 已接受的最新来源版本
 */
public record ReminderBindingState(
        long id,
        boolean reminderEnabled,
        long lastSourceVersion) {
}
