package com.shaopc.worthit.tracking.dataexport.infrastructure.config;

import com.shaopc.worthit.tracking.dataexport.application.TrackingDataExportProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 启用 Tracking 数据导出硬上限配置。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TrackingDataExportProperties.class)
public class TrackingDataExportConfiguration {
}
