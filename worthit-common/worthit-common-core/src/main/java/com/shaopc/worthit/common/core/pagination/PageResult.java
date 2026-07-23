package com.shaopc.worthit.common.core.pagination;

import java.util.List;
import java.util.Objects;

/**
 * 表示不可变的通用分页查询结果。
 *
 * <p>数据集合会被防御性复制，是否存在下一页由页码、每页条数和总条数统一计算。</p>
 *
 * @param <T> 分页元素类型
 */
public final class PageResult<T> {

    private final List<T> items;
    private final int page;
    private final int size;
    private final long total;
    private final boolean hasMore;

    /**
     * 创建并校验分页结果。
     */
    private PageResult(List<T> items, PageQuery pageQuery, long total) {
        this.items = List.copyOf(Objects.requireNonNull(items, "分页数据不能为空"));
        PageQuery requiredPageQuery =
                Objects.requireNonNull(pageQuery, "分页参数不能为空");
        if (total < 0) {
            throw new IllegalArgumentException("总条数不能小于0");
        }
        this.page = requiredPageQuery.page();
        this.size = requiredPageQuery.size();
        this.total = total;
        this.hasMore = (long) page * size < total;
    }

    /**
     * 创建不可变分页结果。
     *
     * @param items     当前页数据
     * @param pageQuery 分页参数
     * @param total     符合查询条件的总条数
     * @param <T>       分页元素类型
     * @return 不可变分页结果
     */
    public static <T> PageResult<T> of(
            List<T> items,
            PageQuery pageQuery,
            long total
    ) {
        return new PageResult<>(items, pageQuery, total);
    }

    /**
     * 获取当前页的不可变数据列表。
     *
     * @return 当前页数据
     */
    public List<T> getItems() {
        return items;
    }

    /**
     * 获取当前页码。
     *
     * @return 从 1 开始的页码
     */
    public int getPage() {
        return page;
    }

    /**
     * 获取每页条数。
     *
     * @return 每页条数
     */
    public int getSize() {
        return size;
    }

    /**
     * 获取符合查询条件的总条数。
     *
     * @return 总条数
     */
    public long getTotal() {
        return total;
    }

    /**
     * 判断当前页之后是否还有数据。
     *
     * @return 存在下一页时为 {@code true}
     */
    public boolean isHasMore() {
        return hasMore;
    }
}
