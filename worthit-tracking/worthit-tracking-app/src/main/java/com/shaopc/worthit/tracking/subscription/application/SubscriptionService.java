package com.shaopc.worthit.tracking.subscription.application;

import com.shaopc.worthit.common.core.pagination.PageResult;

/**
 * 订阅公开应用用例。
 */
public interface SubscriptionService {

    /** 幂等创建订阅。 */
    SubscriptionDetail create(
            String idempotencyKey,
            CreateSubscriptionCommand command);

    /** 查询当前用户订阅详情。 */
    SubscriptionDetail detail(long subscriptionId);

    /** 分页查询当前用户订阅。 */
    PageResult<SubscriptionSummary> list(
            int page,
            int size,
            String keyword,
            Long categoryId);

    /** 按版本幂等更新订阅。 */
    SubscriptionDetail update(
            long subscriptionId,
            String idempotencyKey,
            UpdateSubscriptionCommand command);

    /** 暂停有效订阅。 */
    SubscriptionDetail pause(
            long subscriptionId,
            long version,
            String idempotencyKey);

    /** 结束有效或暂停订阅。 */
    SubscriptionDetail end(
            long subscriptionId,
            long version,
            String idempotencyKey);

    /** 恢复暂停或结束订阅。 */
    SubscriptionDetail resume(
            long subscriptionId,
            String idempotencyKey,
            ResumeSubscriptionCommand command);

    /** 逻辑删除订阅并签发短时恢复令牌。 */
    DeleteSubscriptionResult delete(
            long subscriptionId,
            long version,
            String idempotencyKey);

    /** 在短时窗口内幂等恢复订阅。 */
    SubscriptionDetail restore(
            long subscriptionId,
            long deletedVersion,
            String restoreToken);
}
