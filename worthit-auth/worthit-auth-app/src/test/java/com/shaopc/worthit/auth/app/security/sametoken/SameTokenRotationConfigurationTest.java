package com.shaopc.worthit.auth.app.security.sametoken;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SameTokenRotationConfigurationTest {

    private final ApplicationContextRunner baseContextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(SameTokenRotationConfiguration.class)
                    .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
                    .withBean(MeterRegistry.class, SimpleMeterRegistry::new);

    private final ApplicationContextRunner contextRunner =
            baseContextRunner.withPropertyValues(
                            "worthit.security.same-token.rotation.enabled=true",
                            "worthit.security.same-token.rotation.check-interval=10s",
                            "worthit.security.same-token.rotation.refresh-before=60s",
                            "worthit.security.same-token.rotation.lock-ttl=5s");

    @Test
    void createsSchedulerOnlyWhenRotationIsEnabled() {
        contextRunner.run(context -> assertThat(context)
                .hasNotFailed()
                .hasSingleBean(SameTokenRotationScheduler.class));
        baseContextRunner.run(context -> assertThat(context)
                .hasNotFailed()
                .doesNotHaveBean(SameTokenRotationScheduler.class));
    }

    @Test
    void rejectsNonPositiveCheckInterval() {
        contextRunner
                .withPropertyValues(
                        "worthit.security.same-token.rotation.check-interval=0s")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsNonPositiveRefreshThreshold() {
        contextRunner
                .withPropertyValues(
                        "worthit.security.same-token.rotation.refresh-before=-1s")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsNonPositiveLockTtl() {
        contextRunner
                .withPropertyValues(
                        "worthit.security.same-token.rotation.lock-ttl=0s")
                .run(context -> assertThat(context).hasFailed());
    }
}
