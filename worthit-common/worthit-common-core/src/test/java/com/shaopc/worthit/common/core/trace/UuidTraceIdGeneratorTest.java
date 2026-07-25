package com.shaopc.worthit.common.core.trace;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UuidTraceIdGeneratorTest {

    private final TraceIdGenerator generator = new UuidTraceIdGenerator();

    @Test
    void generatesOpaqueLowercaseHexTraceId() {
        String traceId = generator.generate();

        assertThat(traceId)
                .matches("[0-9a-f]{32}")
                .doesNotContain("-");
    }

    @Test
    void generatesDifferentTraceIds() {
        assertThat(generator.generate()).isNotEqualTo(generator.generate());
    }
}
