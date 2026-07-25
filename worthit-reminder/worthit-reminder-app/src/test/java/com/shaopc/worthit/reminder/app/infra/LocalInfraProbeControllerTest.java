package com.shaopc.worthit.reminder.app.infra;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class LocalInfraProbeControllerTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(LocalInfraProbeController.class);

    @Test
    void existsOnlyWithLocalInfraProfile() {
        contextRunner.run(context ->
                assertThat(context).doesNotHaveBean(
                        LocalInfraProbeController.class));

        contextRunner
                .withInitializer(context ->
                        context.getEnvironment()
                                .setActiveProfiles("local-infra"))
                .run(context -> assertThat(context)
                        .hasSingleBean(LocalInfraProbeController.class));
    }

    @Test
    void exposesOnlyInternalPingWithMinimalResponse() throws Exception {
        RequestMapping mapping =
                LocalInfraProbeController.class.getAnnotation(
                        RequestMapping.class);
        Method ping = LocalInfraProbeController.class.getMethod("ping");
        GetMapping getMapping = ping.getAnnotation(GetMapping.class);

        assertThat(mapping.value()).containsExactly("/internal/__infra");
        assertThat(getMapping.value()).containsExactly("/ping");

        contextRunner
                .withInitializer(context ->
                        context.getEnvironment()
                                .setActiveProfiles("local-infra"))
                .run(context -> {
                    LocalInfraProbeController.ProbeResponse response =
                            context.getBean(LocalInfraProbeController.class)
                                    .ping();
                    assertThat(response.service())
                            .isEqualTo("worthit-reminder");
                    assertThat(response.probe()).isEqualTo("ready");
                    assertThat(response.getClass().getRecordComponents())
                            .extracting(component -> component.getName())
                            .containsExactly("service", "probe");
                });
    }
}
