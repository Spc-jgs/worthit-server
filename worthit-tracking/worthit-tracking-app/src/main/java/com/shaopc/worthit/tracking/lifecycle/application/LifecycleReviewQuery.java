package com.shaopc.worthit.tracking.lifecycle.application;

import com.shaopc.worthit.common.core.pagination.PageQuery;
import com.shaopc.worthit.common.core.pagination.PageResult;

/**
 * 生命周期复盘联合读模型端口。
 */
public interface LifecycleReviewQuery {

    /**
     * 按稳定倒序分页查询当前用户的生命周期事实。
     */
    PageResult<LifecycleReviewEntry> findPage(
            long userId, PageQuery pageQuery);
}
