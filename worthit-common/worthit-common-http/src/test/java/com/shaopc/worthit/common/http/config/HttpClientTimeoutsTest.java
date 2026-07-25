package com.shaopc.worthit.common.http.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class HttpClientTimeoutsTest {

    @Test
    void rejectsZeroOrNegativeTimeouts() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new HttpClientTimeouts(
                        Duration.ZERO, Duration.ofSeconds(1)));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new HttpClientTimeouts(
                        Duration.ofSeconds(1), Duration.ZERO));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new HttpClientTimeouts(
                        Duration.ofMillis(-1), Duration.ofSeconds(1)));
    }
}
