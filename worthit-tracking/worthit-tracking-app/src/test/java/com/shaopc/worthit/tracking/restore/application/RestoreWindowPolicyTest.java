package com.shaopc.worthit.tracking.restore.application;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class RestoreWindowPolicyTest {

    private final RestoreWindowPolicy policy =
            new RestoreWindowPolicy();

    @Test
    void exactDeadlineRemainsRestorable() {
        LocalDateTime deletedAt =
                LocalDateTime.of(2026, 7, 29, 12, 0);

        assertThat(policy.isRestorable(
                deletedAt, deletedAt.plusSeconds(60))).isTrue();
    }

    @Test
    void instantAfterDeadlineIsNotRestorable() {
        LocalDateTime deletedAt =
                LocalDateTime.of(2026, 7, 29, 12, 0);

        assertThat(policy.isRestorable(
                deletedAt,
                deletedAt.plusSeconds(60).plusNanos(1))).isFalse();
    }

    @Test
    void derivesInclusiveDeletionLowerBound() {
        LocalDateTime now =
                LocalDateTime.of(2026, 7, 29, 12, 1);

        assertThat(policy.earliestRestorableDeletion(now))
                .isEqualTo(LocalDateTime.of(
                        2026, 7, 29, 12, 0));
    }
}
