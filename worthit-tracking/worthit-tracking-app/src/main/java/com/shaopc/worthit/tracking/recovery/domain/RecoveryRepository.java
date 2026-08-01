package com.shaopc.worthit.tracking.recovery.domain;

import com.shaopc.worthit.common.core.pagination.PageQuery;
import com.shaopc.worthit.common.core.pagination.PageResult;

/**
 * Tracking 完整恢复只读投影持久化边界。
 */
public interface RecoveryRepository {

    /**
     * 分页查询当前用户已逻辑删除的资源。
     */
    PageResult<DeletedResource> findDeletedPage(
            long userId,
            RecoveryResourceType resourceType,
            PageQuery pageQuery);
}
