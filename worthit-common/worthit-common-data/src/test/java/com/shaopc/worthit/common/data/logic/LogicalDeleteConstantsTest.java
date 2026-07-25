package com.shaopc.worthit.common.data.logic;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogicalDeleteConstantsTest {

    @Test
    void exposesFrozenLogicalDeleteValues() {
        assertThat(LogicalDeleteConstants.ACTIVE).isZero();
        assertThat(LogicalDeleteConstants.DELETED).isOne();
    }
}
