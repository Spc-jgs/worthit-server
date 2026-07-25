package com.shaopc.worthit.reminder.app.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.shaopc.worthit.common.data.config.WorthItMybatisPlusConfiguration;
import com.shaopc.worthit.reminder.app.WorthItReminderApplication;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

class ReminderDataConfigurationTest {

    @Test
    void applicationImportsCommonDataConfiguration() {
        Import importAnnotation =
                WorthItReminderApplication.class.getAnnotation(Import.class);

        assertThat(importAnnotation.value())
                .contains(WorthItMybatisPlusConfiguration.class);
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(
                             WorthItMybatisPlusConfiguration.class)) {
            assertThat(context.getBean(MybatisPlusInterceptor.class)).isNotNull();
        }
    }
}
