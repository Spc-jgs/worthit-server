package com.shaopc.worthit.auth.app.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.shaopc.worthit.auth.app.WorthItAuthApplication;
import com.shaopc.worthit.common.data.config.WorthItMybatisPlusConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

class AuthDataConfigurationTest {

    @Test
    void applicationImportsCommonDataConfiguration() {
        Import importAnnotation =
                WorthItAuthApplication.class.getAnnotation(Import.class);

        assertThat(importAnnotation.value())
                .contains(WorthItMybatisPlusConfiguration.class);
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(
                             WorthItMybatisPlusConfiguration.class)) {
            assertThat(context.getBean(MybatisPlusInterceptor.class)).isNotNull();
        }
    }
}
