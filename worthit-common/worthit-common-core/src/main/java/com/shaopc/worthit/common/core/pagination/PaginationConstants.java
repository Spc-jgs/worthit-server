package com.shaopc.worthit.common.core.pagination;

/**
 * 统一分页参数的默认值和允许边界。
 */
public final class PaginationConstants {

    /**
     * 默认起始页码，页码从 1 开始。
     */
    public static final int DEFAULT_PAGE = 1;

    /**
     * 默认每页条数。
     */
    public static final int DEFAULT_SIZE = 20;

    /**
     * 单次查询允许的最大条数。
     */
    public static final int MAX_SIZE = 50;

    private PaginationConstants() {
    }
}
