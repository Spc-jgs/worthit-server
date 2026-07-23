package com.shaopc.worthit.reminder.client.model;

/**
 * Reminder reconcile 的稳定成功结果码。
 */
public enum ReconcileResultCode {

    /**
     * 命令已应用。
     */
    APPLIED,

    /**
     * 命令来源版本过旧，已忽略。
     */
    IGNORED_OLD
}
