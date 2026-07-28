package com.shaopc.worthit.reminder.app.reconcile.application;

import com.shaopc.worthit.reminder.client.model.ReconcileResultCode;

/**
 * 已持久化的 reconcile 权威命令记录。
 *
 * @param id 命令日志标识
 * @param eventId Outbox 事件标识
 * @param bindingId Binding 标识
 * @param sourceVersion 来源版本
 * @param payloadDigest 规范化请求摘要
 * @param resultCode 原始协调结果
 */
public record ReminderCommandHistory(
        long id,
        String eventId,
        long bindingId,
        long sourceVersion,
        String payloadDigest,
        ReconcileResultCode resultCode) {
}
