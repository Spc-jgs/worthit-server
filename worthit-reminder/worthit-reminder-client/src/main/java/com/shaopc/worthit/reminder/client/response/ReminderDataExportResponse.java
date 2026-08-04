package com.shaopc.worthit.reminder.client.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Reminder 按数据所有权生成的基础数据导出分片。
 */
public record ReminderDataExportResponse(
        int schemaVersion,
        Instant capturedAt,
        String timeZone,
        String userId,
        List<Binding> bindings,
        List<Instance> instances) {

    public ReminderDataExportResponse {
        bindings = List.copyOf(bindings);
        instances = List.copyOf(instances);
    }

    /** 提醒绑定，不包含内部源版本。 */
    public record Binding(
            String id,
            String businessType,
            String businessId,
            String reminderType,
            boolean reminderEnabled,
            Instant createdAt,
            Instant updatedAt) {
    }

    /** 提醒实例历史，不包含源事件和生成列。 */
    public record Instance(
            String id,
            String bindingId,
            LocalDate businessDate,
            Instant remindAt,
            String timezone,
            String status,
            Instant resolvedAt,
            String resolutionReason,
            Instant createdAt,
            Instant updatedAt) {
    }
}
