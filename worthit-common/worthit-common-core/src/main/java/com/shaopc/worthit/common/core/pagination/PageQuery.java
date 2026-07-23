package com.shaopc.worthit.common.core.pagination;

/**
 * 表示从 1 开始计数的通用分页查询参数。
 *
 * @param page 当前页码
 * @param size 每页条数
 */
public record PageQuery(int page, int size) {

    /**
     * 校验分页参数处于统一支持范围内。
     */
    public PageQuery {
        if (page < 1) {
            throw new IllegalArgumentException("页码不能小于1");
        }
        if (size < 1 || size > PaginationConstants.MAX_SIZE) {
            throw new IllegalArgumentException(
                    "每页条数必须在1到" + PaginationConstants.MAX_SIZE + "之间");
        }
    }

    /**
     * 根据可空入参创建分页参数，空值使用统一默认值。
     *
     * @param page 可空页码
     * @param size 可空每页条数
     * @return 已完成边界校验的分页参数
     */
    public static PageQuery of(Integer page, Integer size) {
        return new PageQuery(
                page == null ? PaginationConstants.DEFAULT_PAGE : page,
                size == null ? PaginationConstants.DEFAULT_SIZE : size
        );
    }
}
