package com.shaopc.worthit.tracking.app.config;

import com.shaopc.worthit.tracking.app.WorthItTrackingApplication;
import com.shaopc.worthit.tracking.infrastructure.client.ReminderClientConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

class TrackingDataConfigurationTest {

    @Test
    void applicationOnlyImportsItsServiceSpecificClientConfiguration() {
        Import importAnnotation =
                WorthItTrackingApplication.class.getAnnotation(Import.class);

        assertThat(importAnnotation.value())
                .containsExactly(ReminderClientConfiguration.class);
    }
}
