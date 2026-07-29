package com.shaopc.worthit.tracking.idempotency.infrastructure;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdempotencyRecordStatusTest {

    @Test
    void mapsPersistentCodes() {
        assertThat(IdempotencyRecordStatus.PROCESSING.code())
                .isEqualTo("PROCESSING");
        assertThat(IdempotencyRecordStatus.SUCCEEDED.code())
                .isEqualTo("SUCCEEDED");
        assertThat(IdempotencyRecordStatus.fromCode("PROCESSING"))
                .isSameAs(IdempotencyRecordStatus.PROCESSING);
    }

    @Test
    void rejectsUnknownPersistentCode() {
        assertThatThrownBy(() ->
                IdempotencyRecordStatus.fromCode("FAILED"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("FAILED");
    }
}
