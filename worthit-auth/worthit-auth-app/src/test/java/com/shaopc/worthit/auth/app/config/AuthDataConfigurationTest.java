package com.shaopc.worthit.auth.app.config;

import com.shaopc.worthit.auth.WorthItAuthApplication;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

class AuthDataConfigurationTest {

    @Test
    void applicationDoesNotImportCommonDataConfiguration() {
        assertThat(WorthItAuthApplication.class.isAnnotationPresent(Import.class))
                .isFalse();
    }
}
