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
        assertThat(IdempotencyRecordStatus.FAILED.code())
                .isEqualTo("FAILED");
        assertThat(IdempotencyRecordStatus.fromCode("PROCESSING"))
                .isSameAs(IdempotencyRecordStatus.PROCESSING);
        assertThat(IdempotencyRecordStatus.fromCode("FAILED"))
                .isSameAs(IdempotencyRecordStatus.FAILED);
    }

    @Test
    void rejectsUnknownPersistentCode() {
        assertThatThrownBy(() ->
                IdempotencyRecordStatus.fromCode("UNKNOWN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UNKNOWN");
    }
}
