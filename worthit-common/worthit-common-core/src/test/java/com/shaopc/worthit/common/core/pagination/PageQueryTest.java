package com.shaopc.worthit.common.core.pagination;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageQueryTest {

    @Test
    void shouldApplyDefaultPageAndSize() {
        assertThat(PageQuery.of(null, null))
                .isEqualTo(new PageQuery(
                        PaginationConstants.DEFAULT_PAGE,
                        PaginationConstants.DEFAULT_SIZE));
    }

    @Test
    void shouldAcceptBoundaryValues() {
        assertThat(new PageQuery(1, 1)).isEqualTo(new PageQuery(1, 1));
        assertThat(new PageQuery(1, PaginationConstants.MAX_SIZE).size())
                .isEqualTo(PaginationConstants.MAX_SIZE);
    }

    @Test
    void shouldRejectPageBeforeOne() {
        assertThatThrownBy(() -> new PageQuery(0, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("页码不能小于1");
    }

    @Test
    void shouldRejectSizeOutsideSupportedRange() {
        assertThatThrownBy(() -> new PageQuery(1, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("每页条数必须在1到50之间");

        assertThatThrownBy(() -> new PageQuery(1, 51))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("每页条数必须在1到50之间");
    }
}
