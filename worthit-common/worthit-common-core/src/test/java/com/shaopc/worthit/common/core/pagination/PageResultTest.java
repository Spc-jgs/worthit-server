package com.shaopc.worthit.common.core.pagination;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageResultTest {

    @Test
    void shouldCopyItemsAndCalculateMorePages() {
        List<String> source = new ArrayList<>(List.of("a", "b"));

        PageResult<String> result = PageResult.of(source, new PageQuery(2, 2), 5);
        source.add("c");

        assertThat(result.getItems()).containsExactly("a", "b");
        assertThat(result.getPage()).isEqualTo(2);
        assertThat(result.getSize()).isEqualTo(2);
        assertThat(result.getTotal()).isEqualTo(5);
        assertThat(result.isHasMore()).isTrue();
        assertThatThrownBy(() -> result.getItems().add("x"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldReportNoMorePagesAtOrAfterTheEnd() {
        assertThat(PageResult.of(List.of(), new PageQuery(3, 2), 5).isHasMore())
                .isFalse();
    }

    @Test
    void shouldRejectNegativeTotal() {
        assertThatThrownBy(() -> PageResult.of(List.of(), new PageQuery(1, 20), -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("总条数不能小于0");
    }

    @Test
    void shouldRejectMissingItemsOrQuery() {
        assertThatThrownBy(() -> PageResult.of(null, new PageQuery(1, 20), 0))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("分页数据不能为空");

        assertThatThrownBy(() -> PageResult.of(List.of(), null, 0))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("分页参数不能为空");
    }
}
