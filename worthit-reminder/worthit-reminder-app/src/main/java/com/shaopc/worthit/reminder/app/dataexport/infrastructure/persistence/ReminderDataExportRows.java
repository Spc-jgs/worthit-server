package com.shaopc.worthit.reminder.app.dataexport.infrastructure.persistence;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Reminder 导出专用只读行模型；不承载源版本、事件或生成列。
 */
public final class ReminderDataExportRows {

    private ReminderDataExportRows() {
    }

    /** Binding 行。 */
    @Getter
    @Setter
    public static class BindingRow {
        private Long id;
        private String businessType;
        private Long businessId;
        private String reminderType;
        private Integer reminderEnabled;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    /** Instance 行。 */
    @Getter
    @Setter
    public static class InstanceRow {
        private Long id;
        private Long bindingId;
        private LocalDate businessDate;
        private LocalDateTime remindAt;
        private String timezone;
        private String status;
        private LocalDateTime resolvedAt;
        private String resolutionReason;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }
}
