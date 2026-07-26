package com.shaopc.worthit.tracking.app.config;

import com.shaopc.worthit.tracking.WorthItTrackingApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import static org.assertj.core.api.Assertions.assertThat;

class TrackingDataConfigurationTest {

    @Test
    void applicationScansTheWholeTrackingServicePackage() {
        SpringBootApplication annotation =
                WorthItTrackingApplication.class.getAnnotation(
                        SpringBootApplication.class);

        assertThat(annotation).isNotNull();
        assertThat(WorthItTrackingApplication.class.getPackageName())
                .isEqualTo("com.shaopc.worthit.tracking");
    }
}
