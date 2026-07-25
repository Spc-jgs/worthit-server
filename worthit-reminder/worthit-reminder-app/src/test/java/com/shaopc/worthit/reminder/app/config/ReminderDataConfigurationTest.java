package com.shaopc.worthit.reminder.app.config;

import com.shaopc.worthit.reminder.app.WorthItReminderApplication;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

class ReminderDataConfigurationTest {

    @Test
    void applicationDoesNotImportCommonDataConfiguration() {
        assertThat(WorthItReminderApplication.class
                .isAnnotationPresent(Import.class))
                .isFalse();
    }
}
