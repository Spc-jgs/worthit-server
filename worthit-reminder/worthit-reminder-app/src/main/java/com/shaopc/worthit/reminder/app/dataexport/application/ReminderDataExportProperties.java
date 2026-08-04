package com.shaopc.worthit.reminder.app.dataexport.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Reminder 数据导出的硬上限配置，只允许从冻结上限向下收紧。
 */
@ConfigurationProperties("worthit.data-export")
public record ReminderDataExportProperties(
        @DefaultValue("10000") int maxRecords,
        @DefaultValue("8388608") int maxFragmentBytes) {

    public ReminderDataExportProperties {
        if (maxRecords < 1 || maxRecords > 10_000) {
            throw new IllegalArgumentException("导出记录上限必须在1至10000之间");
        }
        if (maxFragmentBytes < 1 || maxFragmentBytes > 8 * 1024 * 1024) {
            throw new IllegalArgumentException("导出分片上限必须在1至8388608字节之间");
        }
    }
}
