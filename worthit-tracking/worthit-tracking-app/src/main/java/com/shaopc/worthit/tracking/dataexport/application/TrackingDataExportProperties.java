package com.shaopc.worthit.tracking.dataexport.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Tracking 数据导出的硬上限配置，只允许从冻结上限向下收紧。
 */
@ConfigurationProperties("worthit.data-export")
public record TrackingDataExportProperties(
        @DefaultValue("10000") int maxRecords,
        @DefaultValue("8388608") int maxFragmentBytes) {

    private static final int CONTRACT_MAX_RECORDS = 10_000;
    private static final int CONTRACT_MAX_FRAGMENT_BYTES = 8 * 1024 * 1024;

    public TrackingDataExportProperties {
        if (maxRecords < 1 || maxRecords > CONTRACT_MAX_RECORDS) {
            throw new IllegalArgumentException("导出记录上限必须在1至10000之间");
        }
        if (maxFragmentBytes < 1
                || maxFragmentBytes > CONTRACT_MAX_FRAGMENT_BYTES) {
            throw new IllegalArgumentException("导出分片上限必须在1至8388608字节之间");
        }
    }
}
