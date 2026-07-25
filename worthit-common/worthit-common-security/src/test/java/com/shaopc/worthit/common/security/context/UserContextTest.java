package com.shaopc.worthit.common.security.context;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class UserContextTest {

    @Test
    void acceptsPositiveUserId() {
        assertThat(new UserContext(1001L).userId()).isEqualTo(1001L);
    }

    @Test
    void rejectsNonPositiveUserId() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new UserContext(0L))
                .withMessage("用户标识必须大于0");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new UserContext(-1L))
                .withMessage("用户标识必须大于0");
    }
}
