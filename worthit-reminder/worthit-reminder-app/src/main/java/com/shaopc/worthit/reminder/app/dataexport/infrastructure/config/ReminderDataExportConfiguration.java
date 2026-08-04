package com.shaopc.worthit.reminder.app.dataexport.infrastructure.config;

import com.shaopc.worthit.reminder.app.dataexport.application.ReminderDataExportProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 启用 Reminder 数据导出硬上限配置。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ReminderDataExportProperties.class)
public class ReminderDataExportConfiguration {
}
