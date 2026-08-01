package com.shaopc.worthit.tracking.recovery.application;

import com.shaopc.worthit.common.core.pagination.PageResult;
import com.shaopc.worthit.tracking.recovery.domain.RecoveryResourceType;

/**
 * Tracking 完整恢复公开应用用例。
 */
public interface RecoveryService {

    /** 分页查询当前用户已删除资源。 */
    PageResult<RecoveryResourceSummary> list(
            RecoveryResourceType resourceType,
            int page,
            int size);

    /** 按删除后版本幂等执行长期恢复。 */
    RecoveryResult restore(
            RecoveryResourceType resourceType,
            long resourceId,
            long version,
            String idempotencyKey);
}
