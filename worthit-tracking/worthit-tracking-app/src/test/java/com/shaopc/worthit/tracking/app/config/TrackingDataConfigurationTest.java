package com.shaopc.worthit.tracking.app.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.shaopc.worthit.common.data.config.WorthItMybatisPlusConfiguration;
import com.shaopc.worthit.tracking.app.WorthItTrackingApplication;
import com.shaopc.worthit.tracking.infrastructure.client.ReminderClientConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

class TrackingDataConfigurationTest {

    @Test
    void applicationImportsCommonDataConfiguration() {
        Import importAnnotation =
                WorthItTrackingApplication.class.getAnnotation(Import.class);

        assertThat(importAnnotation.value())
                .contains(
                        WorthItMybatisPlusConfiguration.class,
                        ReminderClientConfiguration.class);
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(
                             WorthItMybatisPlusConfiguration.class)) {
            assertThat(context.getBean(MybatisPlusInterceptor.class)).isNotNull();
        }
    }
}
