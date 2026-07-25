package com.shaopc.worthit.common.http.context;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class InternalRequestContextTest {

    @Test
    void rejectsMissingCallerOrProviders() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new InternalRequestContext(
                        " ", () -> "same", () -> "trace"));
        assertThatNullPointerException()
                .isThrownBy(() -> new InternalRequestContext(
                        "worthit-tracking", null, () -> "trace"));
        assertThatNullPointerException()
                .isThrownBy(() -> new InternalRequestContext(
                        "worthit-tracking", () -> "same", null));
    }
}
