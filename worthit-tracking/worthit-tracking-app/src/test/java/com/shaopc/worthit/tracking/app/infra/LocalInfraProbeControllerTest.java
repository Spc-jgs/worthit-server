package com.shaopc.worthit.tracking.app.infra;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class LocalInfraProbeControllerTest {

    private final LocalInfraReminderProbeClient.ReminderProbeResponse
            reminderResponse =
            new LocalInfraReminderProbeClient.ReminderProbeResponse(
                    "worthit-reminder",
                    "ready");
    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(LocalInfraProbeController.class)
                    .withBean(
                            LocalInfraReminderProbeClient.class,
                            () -> () -> reminderResponse)
                    .withPropertyValues(
                            "worthit.runtime.probe-message=phase0-local");

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
    void delegatesReminderProbeAndReturnsOnlyRefreshValue() {
        contextRunner
                .withInitializer(context ->
                        context.getEnvironment()
                                .setActiveProfiles("local-infra"))
                .run(context -> {
                    LocalInfraProbeController controller =
                            context.getBean(LocalInfraProbeController.class);

                    assertThat(controller.reminderPing())
                            .isEqualTo(reminderResponse);
                    assertThat(controller.config().probeMessage())
                            .isEqualTo("phase0-local");
                    assertThat(controller.config().getClass()
                            .getRecordComponents())
                            .extracting(component -> component.getName())
                            .containsExactly("probeMessage");
                });
    }
}
