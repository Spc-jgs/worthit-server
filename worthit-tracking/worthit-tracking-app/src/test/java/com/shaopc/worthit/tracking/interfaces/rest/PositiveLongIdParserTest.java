package com.shaopc.worthit.tracking.interfaces.rest;

import com.shaopc.worthit.common.core.error.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PositiveLongIdParserTest {

    @Test
    void parsesNullablePositiveLong() {
        assertThat(PositiveLongIdParser.parseNullable(null)).isNull();
        assertThat(PositiveLongIdParser.parseNullable("1")).isEqualTo(1L);
        assertThat(PositiveLongIdParser.parseNullable(
                "9223372036854775807"))
                .isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void rejectsMalformedOverflowAndNonPositiveValues() {
        assertInvalid("not-a-number");
        assertInvalid("9223372036854775808");
        assertInvalid("0");
        assertInvalid("-1");
    }

    private static void assertInvalid(String value) {
        assertThatThrownBy(
                () -> PositiveLongIdParser.parseNullable(value))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("正整数");
    }
}
