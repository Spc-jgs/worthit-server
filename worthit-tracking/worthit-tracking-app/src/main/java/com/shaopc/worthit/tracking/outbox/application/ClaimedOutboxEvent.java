package com.shaopc.worthit.tracking.outbox.application;

/**
 * 已由当前 Relay 实例持有租约的 Outbox 事件。
 *
 * @param id 事件主键
 * @param eventId 全局事件标识
 * @param eventType 事件类型
 * @param payloadJson 事件载荷
 * @param schemaVersion 载荷契约版本
 * @param retryCount 已失败次数
 */
public record ClaimedOutboxEvent(
        long id,
        String eventId,
        String eventType,
        String payloadJson,
        int schemaVersion,
        int retryCount) {
}
