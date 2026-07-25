package com.shaopc.worthit.gateway.architecture;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

class GatewayValidationRuntimeTest {

    @Test
    void initializesValidationWithoutTomcatRuntime() {
        assertThatCode(() -> Validation.buildDefaultValidatorFactory().close())
                .doesNotThrowAnyException();
    }
}
