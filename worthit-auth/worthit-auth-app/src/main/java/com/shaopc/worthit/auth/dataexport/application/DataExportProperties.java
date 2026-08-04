package com.shaopc.worthit.auth.dataexport.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Auth 数据导出的实例并发和内存边界。
 *
 * @param maxConcurrent   单实例最大并行导出数
 * @param maxFragmentBytes 单个 JSON 分片最大字节数
 * @param maxArchiveBytes 最终 ZIP 最大字节数
 */
@ConfigurationProperties("worthit.data-export")
public record DataExportProperties(
        @DefaultValue("2") int maxConcurrent,
        @DefaultValue("8388608") int maxFragmentBytes,
        @DefaultValue("20971520") int maxArchiveBytes) {

    private static final int MAX_CONCURRENT = 2;
    private static final int MAX_FRAGMENT_BYTES = 8 * 1024 * 1024;
    private static final int MAX_ARCHIVE_BYTES = 20 * 1024 * 1024;

    /** 校验配置只能在冻结上限内向下收紧。 */
    public DataExportProperties {
        requireRange(maxConcurrent, MAX_CONCURRENT, "导出并发上限");
        requireRange(maxFragmentBytes, MAX_FRAGMENT_BYTES, "导出分片上限");
        requireRange(maxArchiveBytes, MAX_ARCHIVE_BYTES, "导出归档上限");
    }

    private static void requireRange(int value, int maximum, String name) {
        if (value < 1 || value > maximum) {
            throw new IllegalArgumentException(
                    name + "必须在1至" + maximum + "之间");
        }
    }
}
