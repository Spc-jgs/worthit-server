package com.shaopc.worthit.tracking.interfaces.rest;

import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class TrackingRestProtocolTest {

    @Test
    void exposesPublicIdempotencyHeader() {
        assertThat(TrackingHeaderNames.IDEMPOTENCY_KEY)
                .isEqualTo("Idempotency-Key");
    }

    @Test
    void validatesUuidTextWithOneSharedPattern() {
        assertThat(UuidFormat.isValid(
                "123e4567-e89b-42d3-a456-426614174000"))
                .isTrue();
        assertThat(UuidFormat.isValid(
                "123e4567-e89b-02d3-a456-426614174000"))
                .isFalse();
        assertThat(UuidFormat.isValid("not-a-uuid")).isFalse();
        assertThat(UuidFormat.isValid(null)).isFalse();
    }

    @Test
    void parserAndAnnotationsSharePositiveLongPattern() {
        Pattern pattern = Pattern.compile(
                PositiveLongIdParser.PATTERN);

        assertThat(pattern.matcher("1").matches()).isTrue();
        assertThat(pattern.matcher(Long.toString(
                Long.MAX_VALUE)).matches()).isTrue();
        assertThat(pattern.matcher("0").matches()).isFalse();
        assertThat(pattern.matcher("-1").matches()).isFalse();
        assertThat(pattern.matcher("12345678901234567890")
                .matches()).isFalse();
    }
}
